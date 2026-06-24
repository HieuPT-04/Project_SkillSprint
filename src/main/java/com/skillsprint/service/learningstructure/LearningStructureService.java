package com.skillsprint.service.learningstructure;

import com.skillsprint.dto.response.learningstructure.LearningStructureResponse;
import com.skillsprint.entity.Chapter;
import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.Topic;
import com.skillsprint.enums.learningstructure.DifficultyLevel;
import com.skillsprint.enums.learningstructure.GeneratedBy;
import com.skillsprint.enums.learningstructure.LearningStructureStatus;
import com.skillsprint.enums.workspace.WorkspaceStatus;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import com.skillsprint.mapper.LearningStructureMapper;
import com.skillsprint.repository.ChapterRepository;
import com.skillsprint.repository.LearningStructureVersionRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.TopicRepository;
import com.skillsprint.service.learningstructure.ai.AiChapterDraft;
import com.skillsprint.service.learningstructure.ai.AiLearningStructureDraft;
import com.skillsprint.service.learningstructure.ai.AiTopicDraft;
import com.skillsprint.service.learningstructure.ai.GeminiLearningStructureClient;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentAnalysis;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentKind;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.SyllabusSlot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.skillsprint.service.subscription.QuotaService;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LearningStructureService {

    static int TOPICS_PER_CHAPTER = 3;
    static int MAX_LIST_ITEMS = 6;
    static int MAX_LIST_ITEM_LENGTH = 120;
    static Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,5})\\s+(.{3,160})$");
    static Pattern NUMBERED_HEADING_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+){0,4})[.)]?\\s+(.{3,160})$");
    static Pattern LIST_MARKER_PATTERN = Pattern.compile("^[-*•]\\s+.+$");
    static Pattern DISPLAY_STEP_PREFIX_PATTERN = Pattern.compile(
            "^(?:bước|step|topic)\\s*\\d+(?:\\s*[.\\-:/)]\\s*|\\s+)(.+)$",
            Pattern.CASE_INSENSITIVE
    );
    // Strips raw outline/document numbering only when the separator is followed by whitespace,
    // e.g. "1. Tổng quan", "1) X", "1.1. Backend", "2 - Y", "3: Z", "4/ W".
    // The mandatory trailing whitespace is what keeps clock times like "08:00" from being treated
    // as a numeric prefix (there is no space after the ":"). Numeric titles without a separator
    // (e.g. "2026 Roadmap", "5 Whys") are preserved.
    static Pattern NUMERIC_PREFIX_PATTERN = Pattern.compile(
            "^\\s*(?:\\d+(?:\\.\\d+)*[.)]\\s+|\\d+\\s*[-:/]\\s+)(.+)$"
    );
    // Clock times / time ranges (e.g. "08:00", "08:00 - 10:00") are real content, never outline
    // numbering or headings. Used as guards so prefix cleanup and heading detection leave them alone.
    static Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "^\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*[-–—]\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*$"
    );
    static Pattern TIME_PATTERN = Pattern.compile("^\\s*\\d{1,2}:\\d{2}(?::\\d{2})?\\s*$");
    static String DEFAULT_DISPLAY_TITLE = "Nội dung học";

    StudyWorkspaceRepository workspaceRepository;
    MaterialChunkRepository materialChunkRepository;
    LearningStructureVersionRepository structureVersionRepository;
    ChapterRepository chapterRepository;
    TopicRepository topicRepository;
    LearningStructureMapper learningStructureMapper;
    GeminiLearningStructureClient geminiLearningStructureClient;
    QuotaService quotaService;

    @Transactional
    public LearningStructureResponse generate(String userId, UUID workspaceId) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        quotaService.validateCanGenerateAi(userId);
        List<MaterialChunk> chunks = materialChunkRepository
                .findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspaceId);

        if (chunks.isEmpty()) {
            throw new AppException(ErrorCode.MATERIAL_CHUNKS_NOT_READY);
        }

        DocumentAnalysis documentAnalysis = LearningDocumentAnalyzer.analyze(chunks);
        AiLearningStructureDraft aiDraft = geminiLearningStructureClient.generate(chunks, documentAnalysis);
        boolean useAi = isValidAiDraft(aiDraft, chunks, documentAnalysis);

        LearningStructureVersion structureVersion = new LearningStructureVersion();
        structureVersion.setWorkspace(workspace);
        structureVersion.setVersionNo(nextVersionNo(workspaceId));
        structureVersion.setStatus(LearningStructureStatus.REVIEW_REQUIRED);
        structureVersion.setInputSummary(buildInputSummary(chunks));

        if (useAi) {
            structureVersion.setGeneratedBy(GeneratedBy.AI);
            structureVersion.setAiModel("gemini");
            structureVersion.setConfidenceScore(normalizeConfidence(aiDraft.confidenceScore()));
            structureVersion.setWarnings(safeList(aiDraft.warnings()));
        } else {
            structureVersion.setGeneratedBy(GeneratedBy.SYSTEM);
            structureVersion.setAiModel(fallbackAiModel(documentAnalysis));
            structureVersion.setConfidenceScore(BigDecimal.valueOf(0.70));
            structureVersion.setWarnings(List.of(fallbackWarning(documentAnalysis)));
        }

        LearningStructureVersion savedVersion = structureVersionRepository.saveAndFlush(structureVersion);
        GeneratedStructure generatedStructure;
        if (useAi) {
            generatedStructure = createAiStructure(savedVersion, workspace, aiDraft, chunks);
        } else {
            List<HeadingSection> headingSections = detectHeadingSections(chunks);
            if (documentAnalysis.isSyllabus() && !documentAnalysis.syllabusSlots().isEmpty()) {
                generatedStructure = createSyllabusBasedStructure(savedVersion, workspace, documentAnalysis.syllabusSlots());
            } else {
                generatedStructure = headingSections.isEmpty()
                        ? createFallbackStructure(savedVersion, workspace, chunks)
                        : createHeadingBasedStructure(savedVersion, workspace, headingSections);
            }
        }

        return learningStructureMapper.toResponse(
                savedVersion,
                generatedStructure.chapters(),
                generatedStructure.topics()
        );
    }

    @Transactional(readOnly = true)
    public LearningStructureResponse getLatest(String userId, UUID workspaceId) {
        findOwnedWorkspace(userId, workspaceId);
        LearningStructureVersion structureVersion = structureVersionRepository
                .findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.LEARNING_STRUCTURE_NOT_FOUND));

        return buildResponse(structureVersion);
    }

    @Transactional
    public LearningStructureResponse confirm(String userId, UUID workspaceId) {
        findOwnedWorkspace(userId, workspaceId);
        LearningStructureVersion structureVersion = structureVersionRepository
                .findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .orElseThrow(() -> new AppException(ErrorCode.LEARNING_STRUCTURE_NOT_FOUND));

        if (structureVersion.getStatus() == LearningStructureStatus.CONFIRMED) {
            throw new AppException(ErrorCode.LEARNING_STRUCTURE_ALREADY_CONFIRMED);
        }

        structureVersion.setStatus(LearningStructureStatus.CONFIRMED);
        structureVersion.setConfirmedAt(Instant.now());

        return buildResponse(structureVersion);
    }

    private LearningStructureResponse buildResponse(LearningStructureVersion structureVersion) {
        UUID structureVersionId = structureVersion.getStructureVersionId();
        List<Chapter> chapters = chapterRepository
                .findByStructureVersionStructureVersionIdOrderBySequenceNoAsc(structureVersionId);
        List<Topic> topics = topicRepository
                .findByStructureVersionStructureVersionIdOrderByChapterSequenceNoAscSequenceNoAsc(structureVersionId);

        return learningStructureMapper.toResponse(structureVersion, chapters, topics);
    }

    private StudyWorkspace findOwnedWorkspace(String userId, UUID workspaceId) {
        return workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(
                        workspaceId,
                        userId,
                        WorkspaceStatus.DELETED
                )
                .orElseThrow(() -> new AppException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private int nextVersionNo(UUID workspaceId) {
        return structureVersionRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(workspaceId)
                .map(version -> version.getVersionNo() + 1)
                .orElse(1);
    }

    private GeneratedStructure createFallbackStructure(
            LearningStructureVersion structureVersion,
            StudyWorkspace workspace,
            List<MaterialChunk> chunks
    ) {
        List<Chapter> chapters = createChapters(structureVersion, workspace, chunks);
        List<Topic> topics = createTopics(structureVersion, workspace, chapters, chunks);
        return new GeneratedStructure(chapters, topics);
    }

    private GeneratedStructure createAiStructure(
            LearningStructureVersion structureVersion,
            StudyWorkspace workspace,
            AiLearningStructureDraft aiDraft,
            List<MaterialChunk> chunks
    ) {
        List<AiChapterDraft> chapterDrafts = aiDraft.chapters();
        List<Chapter> chapters = new ArrayList<>();

        for (int i = 0; i < chapterDrafts.size(); i++) {
            AiChapterDraft draft = chapterDrafts.get(i);
            Chapter chapter = new Chapter();
            chapter.setWorkspace(workspace);
            chapter.setStructureVersion(structureVersion);
            chapter.setTitle(cleanDisplayTitle(defaultText(draft.title(), "Chương " + (i + 1)), 90));
            chapter.setSummary(truncate(defaultText(draft.summary(), chapter.getTitle()), 1200));
            chapter.setWhatToLearn(safeList(draft.whatToLearn()));
            chapter.setKeyConcepts(safeList(draft.keyConcepts()));
            chapter.setLearningOutcomes(safeList(draft.learningOutcomes()));
            chapter.setRecommendedFocus(safeList(draft.recommendedFocus()));
            chapter.setDifficulty(defaultDifficulty(draft.difficulty(), i));
            chapter.setEstimatedMinutes(defaultMinutes(draft.estimatedMinutes(), 30));
            chapter.setSequenceNo(i + 1);
            chapter.setSourceChunkIds(collectAiChapterSourceChunkIds(draft, chunks));
            chapter.setAiGenerated(true);
            chapters.add(chapter);
        }

        List<Chapter> savedChapters = chapterRepository.saveAllAndFlush(chapters);
        List<Topic> topics = new ArrayList<>();

        for (int chapterIndex = 0; chapterIndex < chapterDrafts.size(); chapterIndex++) {
            Chapter chapter = savedChapters.get(chapterIndex);
            AiChapterDraft chapterDraft = chapterDrafts.get(chapterIndex);
            List<AiTopicDraft> topicDrafts = chapterDraft.topics();

            for (int topicIndex = 0; topicIndex < topicDrafts.size(); topicIndex++) {
                AiTopicDraft draft = topicDrafts.get(topicIndex);
                Topic topic = new Topic();
                topic.setChapter(chapter);
                topic.setWorkspace(workspace);
                topic.setStructureVersion(structureVersion);
                topic.setTitle(cleanDisplayTitle(defaultText(draft.title(), "Nội dung " + (topicIndex + 1)), 90));
                topic.setSummaryContent(truncate(defaultText(draft.summaryContent(), topic.getTitle()), 1200));
                topic.setWhatToLearn(safeList(draft.whatToLearn()));
                topic.setKeyConcepts(safeList(draft.keyConcepts()));
                topic.setLearningOutcomes(safeList(draft.learningOutcomes()));
                topic.setRecommendedFocus(safeList(draft.recommendedFocus()));
                topic.setDifficulty(draft.difficulty() == null ? chapter.getDifficulty() : draft.difficulty());
                topic.setEstimatedMinutes(defaultMinutes(draft.estimatedMinutes(), 15));
                topic.setSequenceNo(topicIndex + 1);
                topic.setSourceChunkIds(validSourceChunkIds(draft.sourceChunkIds(), chunks));
                topic.setAiGenerated(true);
                topics.add(topic);
            }
        }

        return new GeneratedStructure(savedChapters, topicRepository.saveAllAndFlush(topics));
    }

    private GeneratedStructure createHeadingBasedStructure(
            LearningStructureVersion structureVersion,
            StudyWorkspace workspace,
            List<HeadingSection> sections
    ) {
        List<String> sectionTitles = sections.stream().map(HeadingSection::title).toList();
        if (isReportLikeHeadings(sectionTitles)) {
            return createReportStructure(structureVersion, workspace, sections);
        }

        int chapterLevel = sections.stream()
                .mapToInt(HeadingSection::level)
                .min()
                .orElse(1);
        List<ChapterDraft> chapterDrafts = new ArrayList<>();
        ChapterDraft currentChapter = null;

        for (HeadingSection section : sections) {
            if (section.level() == chapterLevel || currentChapter == null) {
                currentChapter = new ChapterDraft(section);
                chapterDrafts.add(currentChapter);
                continue;
            }
            currentChapter.topicSections().add(section);
        }

        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < chapterDrafts.size(); i++) {
            ChapterDraft draft = chapterDrafts.get(i);
            HeadingSection section = draft.chapterSection();
            List<MaterialChunk> chapterChunks = collectChapterChunks(draft);
            Chapter chapter = new Chapter();
            chapter.setWorkspace(workspace);
            chapter.setStructureVersion(structureVersion);
            chapter.setTitle(cleanDisplayTitle(section.title(), 90));
            chapter.setSummary(truncate(collectChapterText(draft), 700));
            chapter.setWhatToLearn(List.of("Nắm nội dung chính của " + chapter.getTitle(), "Xác định các ý nhỏ trong chương"));
            chapter.setKeyConcepts(extractKeyConcepts(chapterChunks));
            chapter.setLearningOutcomes(List.of("Tóm tắt được " + chapter.getTitle()));
            chapter.setRecommendedFocus(List.of("Đọc theo thứ tự heading trong tài liệu", "Ghi chú các thuật ngữ xuất hiện nhiều lần"));
            chapter.setDifficulty(resolveDifficulty(i));
            chapter.setEstimatedMinutes(estimateMinutes(chapterChunks, 30));
            chapter.setSequenceNo(i + 1);
            chapter.setSourceChunkIds(toChunkIds(chapterChunks));
            chapter.setAiGenerated(false);
            chapters.add(chapter);
        }
        List<Chapter> savedChapters = chapterRepository.saveAllAndFlush(chapters);

        List<Topic> topics = new ArrayList<>();
        for (int i = 0; i < chapterDrafts.size(); i++) {
            Chapter chapter = savedChapters.get(i);
            ChapterDraft draft = chapterDrafts.get(i);
            List<HeadingSection> topicSections = draft.topicSections().isEmpty()
                    ? List.of(draft.chapterSection())
                    : draft.topicSections();

            for (int topicIndex = 0; topicIndex < topicSections.size(); topicIndex++) {
                HeadingSection section = topicSections.get(topicIndex);
                Topic topic = new Topic();
                topic.setChapter(chapter);
                topic.setWorkspace(workspace);
                topic.setStructureVersion(structureVersion);
                topic.setTitle(cleanDisplayTitle(section.title(), 90));
                topic.setSummaryContent(truncate(section.text(), 700));
                topic.setWhatToLearn(List.of("Đọc và hiểu: " + topic.getTitle()));
                topic.setKeyConcepts(extractKeyConcepts(section.chunks()));
                topic.setLearningOutcomes(List.of("Trình bày được ý chính của " + topic.getTitle()));
                topic.setRecommendedFocus(List.of("Tập trung vào ví dụ và thuật ngữ chính"));
                topic.setDifficulty(chapter.getDifficulty());
                topic.setEstimatedMinutes(estimateMinutes(section.chunks(), 15));
                topic.setSequenceNo(topicIndex + 1);
                topic.setSourceChunkIds(toChunkIds(section.chunks()));
                topic.setAiGenerated(false);
                topics.add(topic);
            }
        }

        return new GeneratedStructure(savedChapters, topicRepository.saveAllAndFlush(topics));
    }

    // Technical reports (bug reports, RFCs, post-mortems) come as a flat list of sections such as
    // Summary / Root Cause / Fix / Tests. Copying each one verbatim into its own chapter produces a
    // useless, English, outline-shaped result. Instead, group the sections into learner-friendly
    // phases (overview → cause → fix → validation → result) with Vietnamese chapter titles.
    private GeneratedStructure createReportStructure(
            LearningStructureVersion structureVersion,
            StudyWorkspace workspace,
            List<HeadingSection> sections
    ) {
        List<List<HeadingSection>> phaseGroups = new ArrayList<>();
        List<HeadingSection> currentGroup = new ArrayList<>();
        ReportPhase currentPhase = null;

        for (HeadingSection section : sections) {
            ReportPhase phase = classifyReportPhase(section.title());
            if (phase == null) {
                // Sections with no learning phase (stray fragments) are absorbed into the previous
                // group's content rather than promoted into their own chapter/topic.
                if (!currentGroup.isEmpty()) {
                    currentGroup.get(currentGroup.size() - 1).append(section.title());
                    section.chunks().forEach(currentGroup.get(currentGroup.size() - 1)::addChunk);
                }
                continue;
            }
            if (currentPhase != null && phase != currentPhase) {
                phaseGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
            }
            currentGroup.add(section);
            currentPhase = phase;
        }
        if (!currentGroup.isEmpty()) {
            phaseGroups.add(currentGroup);
        }

        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < phaseGroups.size(); i++) {
            List<HeadingSection> group = phaseGroups.get(i);
            List<MaterialChunk> groupChunks = group.stream()
                    .flatMap(section -> section.chunks().stream())
                    .distinct()
                    .toList();
            String chapterTitle = classifyReportPhase(group.get(0).title()).chapterTitle();
            Chapter chapter = new Chapter();
            chapter.setWorkspace(workspace);
            chapter.setStructureVersion(structureVersion);
            chapter.setTitle(truncate(chapterTitle, 90));
            chapter.setSummary(truncate(buildReportChapterSummary(group), 700));
            chapter.setWhatToLearn(List.of("Hiểu phần " + chapterTitle.toLowerCase(), "Nắm các ý chính trong nhóm nội dung này"));
            chapter.setKeyConcepts(group.stream()
                    .map(section -> localizeReportHeading(section.title()))
                    .distinct()
                    .limit(5)
                    .toList());
            chapter.setLearningOutcomes(List.of("Trình bày lại được " + chapterTitle.toLowerCase()));
            chapter.setRecommendedFocus(List.of("Đọc theo trình tự nhóm nội dung", "Ghi chú các điểm kỹ thuật quan trọng"));
            chapter.setDifficulty(resolveDifficulty(i));
            chapter.setEstimatedMinutes(estimateMinutes(groupChunks, 30));
            chapter.setSequenceNo(i + 1);
            chapter.setSourceChunkIds(toChunkIds(groupChunks));
            chapter.setAiGenerated(false);
            chapters.add(chapter);
        }

        List<Chapter> savedChapters = chapterRepository.saveAllAndFlush(chapters);

        List<Topic> topics = new ArrayList<>();
        for (int i = 0; i < phaseGroups.size(); i++) {
            Chapter chapter = savedChapters.get(i);
            List<HeadingSection> group = phaseGroups.get(i);
            for (int topicIndex = 0; topicIndex < group.size(); topicIndex++) {
                HeadingSection section = group.get(topicIndex);
                String topicTitle = localizeReportHeading(section.title());
                Topic topic = new Topic();
                topic.setChapter(chapter);
                topic.setWorkspace(workspace);
                topic.setStructureVersion(structureVersion);
                topic.setTitle(cleanDisplayTitle(topicTitle, 90));
                topic.setSummaryContent(truncate(section.text(), 700));
                topic.setWhatToLearn(List.of("Đọc và hiểu: " + topic.getTitle()));
                topic.setKeyConcepts(List.of(topic.getTitle()));
                topic.setLearningOutcomes(List.of("Trình bày được ý chính của " + topic.getTitle()));
                topic.setRecommendedFocus(List.of("Tập trung vào điểm kỹ thuật và lý do thay đổi"));
                topic.setDifficulty(chapter.getDifficulty());
                topic.setEstimatedMinutes(estimateMinutes(section.chunks(), 15));
                topic.setSequenceNo(topicIndex + 1);
                topic.setSourceChunkIds(toChunkIds(section.chunks()));
                topic.setAiGenerated(false);
                topics.add(topic);
            }
        }

        return new GeneratedStructure(savedChapters, topicRepository.saveAllAndFlush(topics));
    }

    private String buildReportChapterSummary(List<HeadingSection> group) {
        return group.stream()
                .map(section -> localizeReportHeading(section.title()))
                .reduce((left, right) -> left + "; " + right)
                .orElse("Nhóm nội dung học");
    }

    // True when the heading list looks like a technical report: most headings map to a known
    // report phase and at least three distinct phases are present.
    static boolean isReportLikeHeadings(List<String> titles) {
        if (titles == null || titles.size() < 4) {
            return false;
        }
        long classified = titles.stream().filter(title -> classifyReportPhase(title) != null).count();
        long distinctPhases = titles.stream()
                .map(LearningStructureService::classifyReportPhase)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return classified * 2 >= titles.size() && distinctPhases >= 3;
    }

    static ReportPhase classifyReportPhase(String title) {
        String normalized = title == null ? "" : title.toLowerCase().trim();
        if (containsAny(normalized, "summary", "tổng quan", "tong quan", "overview", "affected area",
                "phạm vi", "pham vi", "context", "bối cảnh", "boi canh", "symptom", "triệu chứng",
                "background", "introduction", "giới thiệu")) {
            return ReportPhase.OVERVIEW;
        }
        if (containsAny(normalized, "root cause", "nguyên nhân", "nguyen nhan", "why", "vì sao", "vi sao",
                "impact", "tác động", "tac dong", "analysis", "phân tích", "phan tich")) {
            return ReportPhase.ROOT_CAUSE;
        }
        if (containsAny(normalized, "fix", "khắc phục", "khac phuc", "cách sửa", "cach sua", "solution",
                "giải pháp", "giai phap", "implementation", "implemented", "triển khai", "trien khai",
                "expected behavior", "expected behaviour", "hành vi mong đợi", "change")) {
            return ReportPhase.FIX;
        }
        if (containsAny(normalized, "test", "kiểm thử", "kiem thu", "verification", "verify", "xác minh",
                "xac minh", "qa", "validation")) {
            return ReportPhase.VALIDATION;
        }
        if (containsAny(normalized, "result", "kết quả", "ket qua", "conclusion", "kết luận", "ket luan",
                "outcome")) {
            return ReportPhase.RESULT;
        }
        return null;
    }

    // Maps common English technical-report section headings to clean Vietnamese display titles.
    static String localizeReportHeading(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_DISPLAY_TITLE;
        }
        String normalized = title.toLowerCase().trim();
        return switch (normalized) {
            case "summary" -> "Tổng quan";
            case "affected area" -> "Phạm vi ảnh hưởng";
            case "root cause" -> "Nguyên nhân gốc";
            case "impact" -> "Tác động";
            case "fix implemented", "fix" -> "Cách sửa";
            case "expected behavior", "expected behaviour" -> "Hành vi mong đợi";
            case "tests added / updated", "tests added/updated", "tests" -> "Kiểm thử đã bổ sung";
            case "verification" -> "Xác minh";
            case "final result" -> "Kết quả cuối cùng";
            default -> title.trim();
        };
    }

    private GeneratedStructure createSyllabusBasedStructure(
            LearningStructureVersion structureVersion,
            StudyWorkspace workspace,
            List<SyllabusSlot> slots
    ) {
        List<List<SyllabusSlot>> groups = groupSyllabusSlots(slots);
        List<Chapter> chapters = new ArrayList<>();

        for (int i = 0; i < groups.size(); i++) {
            List<SyllabusSlot> group = groups.get(i);
            Chapter chapter = new Chapter();
            chapter.setWorkspace(workspace);
            chapter.setStructureVersion(structureVersion);
            chapter.setTitle(cleanDisplayTitle(inferSyllabusChapterTitle(group), 90));
            chapter.setSummary(truncate(buildSyllabusChapterSummary(group), 700));
            chapter.setWhatToLearn(group.stream()
                    .map(SyllabusSlot::topic)
                    .map(topic -> "Học: " + truncate(topic, 80))
                    .limit(4)
                    .toList());
            chapter.setKeyConcepts(extractSyllabusConcepts(group));
            chapter.setLearningOutcomes(List.of("Nắm được nhóm nội dung " + chapter.getTitle()));
            chapter.setRecommendedFocus(List.of("Học theo thứ tự slot trong syllabus", "Ưu tiên thực hành sau mỗi nhóm kiến thức"));
            chapter.setDifficulty(resolveDifficulty(i));
            chapter.setEstimatedMinutes(Math.max(30, group.size() * 25));
            chapter.setSequenceNo(i + 1);
            chapter.setSourceChunkIds(group.stream()
                    .flatMap(slot -> slot.sourceChunkIds().stream())
                    .distinct()
                    .toList());
            chapter.setAiGenerated(false);
            chapters.add(chapter);
        }

        List<Chapter> savedChapters = chapterRepository.saveAllAndFlush(chapters);
        List<Topic> topics = new ArrayList<>();

        for (int chapterIndex = 0; chapterIndex < groups.size(); chapterIndex++) {
            Chapter chapter = savedChapters.get(chapterIndex);
            List<SyllabusSlot> group = groups.get(chapterIndex);

            for (int topicIndex = 0; topicIndex < group.size(); topicIndex++) {
                SyllabusSlot slot = group.get(topicIndex);
                Topic topic = new Topic();
                topic.setChapter(chapter);
                topic.setWorkspace(workspace);
                topic.setStructureVersion(structureVersion);
                topic.setTitle(cleanDisplayTitle(slot.topic(), 90));
                topic.setSummaryContent(truncate(buildSyllabusTopicSummary(slot), 700));
                topic.setWhatToLearn(List.of("Học nội dung slot " + slot.slot(), "Thực hành phần: " + truncate(slot.topic(), 70)));
                topic.setKeyConcepts(extractSyllabusConcepts(List.of(slot)));
                topic.setLearningOutcomes(List.of("Trình bày và áp dụng được " + truncate(slot.topic(), 80)));
                topic.setRecommendedFocus(List.of("Bám theo bài tập/lab trong syllabus"));
                topic.setDifficulty(chapter.getDifficulty());
                topic.setEstimatedMinutes(estimateSyllabusTopicMinutes(slot));
                topic.setSequenceNo(topicIndex + 1);
                topic.setSourceChunkIds(slot.sourceChunkIds());
                topic.setAiGenerated(false);
                topics.add(topic);
            }
        }

        return new GeneratedStructure(savedChapters, topicRepository.saveAllAndFlush(topics));
    }

    private List<Chapter> createChapters(
            LearningStructureVersion structureVersion,
            StudyWorkspace workspace,
            List<MaterialChunk> chunks
    ) {
        List<List<MaterialChunk>> groups = groupChunks(chunks, TOPICS_PER_CHAPTER);
        List<Chapter> chapters = new ArrayList<>();

        for (int i = 0; i < groups.size(); i++) {
            List<MaterialChunk> group = groups.get(i);
            String title = extractTitle(group.get(0).getContent(), "Chương " + (i + 1));
            Chapter chapter = new Chapter();
            chapter.setWorkspace(workspace);
            chapter.setStructureVersion(structureVersion);
            chapter.setTitle(title);
            chapter.setSummary(buildSummary(group));
            chapter.setWhatToLearn(List.of("Nắm các ý chính trong " + title, "Liên hệ kiến thức với mục tiêu học tập"));
            chapter.setKeyConcepts(extractKeyConcepts(group));
            chapter.setLearningOutcomes(List.of("Giải thích được nội dung chính của " + title));
            chapter.setRecommendedFocus(List.of("Đọc kỹ tài liệu gốc", "Ghi chú các khái niệm chưa rõ"));
            chapter.setDifficulty(resolveDifficulty(i));
            chapter.setEstimatedMinutes(Math.max(30, group.size() * 25));
            chapter.setSequenceNo(i + 1);
            chapter.setSourceChunkIds(toChunkIds(group));
            chapter.setAiGenerated(false);
            chapters.add(chapter);
        }

        return chapterRepository.saveAllAndFlush(chapters);
    }

    private List<Topic> createTopics(
            LearningStructureVersion structureVersion,
            StudyWorkspace workspace,
            List<Chapter> chapters,
            List<MaterialChunk> chunks
    ) {
        List<Topic> topics = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            MaterialChunk chunk = chunks.get(i);
            Chapter chapter = chapters.get(i / TOPICS_PER_CHAPTER);
            int topicNo = (i % TOPICS_PER_CHAPTER) + 1;
            String title = extractTitle(chunk.getContent(), "Topic " + topicNo);

            Topic topic = new Topic();
            topic.setChapter(chapter);
            topic.setWorkspace(workspace);
            topic.setStructureVersion(structureVersion);
            topic.setTitle(title);
            topic.setSummaryContent(truncate(chunk.getContent(), 500));
            topic.setWhatToLearn(List.of("Đọc và hiểu: " + title));
            topic.setKeyConcepts(extractKeyConcepts(List.of(chunk)));
            topic.setLearningOutcomes(List.of("Tóm tắt được " + title));
            topic.setRecommendedFocus(List.of("Tập trung vào ví dụ và thuật ngữ chính"));
            topic.setDifficulty(chapter.getDifficulty());
            topic.setEstimatedMinutes(Math.max(15, chunk.getTokenCount() == null ? 20 : chunk.getTokenCount() / 8));
            topic.setSequenceNo(topicNo);
            topic.setSourceChunkIds(List.of(chunk.getChunkId().toString()));
            topic.setAiGenerated(false);
            topics.add(topic);
        }

        return topicRepository.saveAllAndFlush(topics);
    }

    private List<List<MaterialChunk>> groupChunks(List<MaterialChunk> chunks, int groupSize) {
        List<List<MaterialChunk>> groups = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += groupSize) {
            groups.add(chunks.subList(i, Math.min(i + groupSize, chunks.size())));
        }
        return groups;
    }

    private String buildInputSummary(List<MaterialChunk> chunks) {
        int tokenCount = chunks.stream()
                .map(MaterialChunk::getTokenCount)
                .filter(token -> token != null)
                .mapToInt(Integer::intValue)
                .sum();
        return "Generated from " + chunks.size() + " material chunks, estimated " + tokenCount + " tokens.";
    }

    private boolean isValidAiDraft(AiLearningStructureDraft draft, List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        if (draft == null || chunks == null || chunks.isEmpty()) {
            return false;
        }
        if (draft.chapters() == null || draft.chapters().isEmpty()) {
            return false;
        }

        if (analysis != null && analysis.isSyllabus() && !isValidSyllabusAiDraft(draft, analysis)) {
            return false;
        }
        if (analysis != null && analysis.kind() != DocumentKind.SYLLABUS && !isValidDocumentAwareAiDraft(draft, analysis)) {
            return false;
        }

        for (AiChapterDraft chapter : draft.chapters()) {
            if (chapter == null || defaultText(chapter.title(), "").isBlank()) {
                return false;
            }
            if (chapter.topics() == null || chapter.topics().isEmpty()) {
                return false;
            }
            for (AiTopicDraft topic : chapter.topics()) {
                if (topic == null || defaultText(topic.title(), "").isBlank()) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isValidDocumentAwareAiDraft(AiLearningStructureDraft draft, DocumentAnalysis analysis) {
        int sectionCount = analysis.sections() == null ? 0 : analysis.sections().size();
        int chapterCount = draft.chapters().size();
        int topicCount = draft.chapters().stream()
                .filter(Objects::nonNull)
                .map(AiChapterDraft::topics)
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum();

        if (analysis.kind() == DocumentKind.LECTURE_NOTE && sectionCount >= 4 && chapterCount == 1) {
            return false;
        }
        if (analysis.kind() == DocumentKind.SLIDE_DECK && sectionCount >= 6 && chapterCount == 1) {
            return false;
        }
        if (analysis.kind() == DocumentKind.ASSIGNMENT && chapterCount > 6) {
            return false;
        }
        if (sectionCount >= 4 && topicCount < 2) {
            return false;
        }

        return draft.chapters().stream()
                .filter(Objects::nonNull)
                .map(AiChapterDraft::title)
                .noneMatch(title -> isBadGenericChapterTitle(title, analysis.kind()));
    }

    private boolean isValidSyllabusAiDraft(AiLearningStructureDraft draft, DocumentAnalysis analysis) {
        int slotCount = analysis.syllabusSlots() == null ? 0 : analysis.syllabusSlots().size();
        int chapterCount = draft.chapters().size();
        int topicCount = draft.chapters().stream()
                .filter(Objects::nonNull)
                .map(AiChapterDraft::topics)
                .filter(Objects::nonNull)
                .mapToInt(List::size)
                .sum();

        if (chapterCount == 1 && slotCount >= 3) {
            return false;
        }
        if (slotCount >= 10 && chapterCount < 4) {
            return false;
        }
        if (slotCount >= 5 && chapterCount < 3) {
            return false;
        }
        if (slotCount >= 5 && topicCount < Math.min(slotCount, 5)) {
            return false;
        }

        return draft.chapters().stream()
                .filter(Objects::nonNull)
                .map(AiChapterDraft::title)
                .noneMatch(this::isBadSyllabusChapterTitle);
    }

    private BigDecimal normalizeConfidence(BigDecimal confidenceScore) {
        if (confidenceScore == null) {
            return BigDecimal.valueOf(0.80);
        }
        if (confidenceScore.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (confidenceScore.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return confidenceScore;
    }

    private DifficultyLevel defaultDifficulty(DifficultyLevel difficulty, int chapterIndex) {
        return difficulty == null ? resolveDifficulty(chapterIndex) : difficulty;
    }

    private int defaultMinutes(Integer minutes, int minimumMinutes) {
        return minutes == null || minutes < minimumMinutes ? minimumMinutes : minutes;
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    static String cleanDisplayTitle(String value, int maxLength) {
        String title = defaultText(value, DEFAULT_DISPLAY_TITLE).replaceAll("\\s+", " ").trim();

        // Never run prefix cleanup on clock times / time ranges; multi-pass stripping would
        // otherwise corrupt "08:00 - 10:00" into "00".
        if (TIME_RANGE_PATTERN.matcher(title).matches() || TIME_PATTERN.matcher(title).matches()) {
            return truncate(title, maxLength);
        }

        // Combined prefixes such as "Bước 1: 1. Tổng quan" need more than one pass.
        for (int pass = 0; pass < 3; pass++) {
            String previous = title;
            title = stripPrefix(DISPLAY_STEP_PREFIX_PATTERN, title);
            title = stripPrefix(NUMERIC_PREFIX_PATTERN, title);
            if (title.equals(previous) || title.isBlank()) {
                break;
            }
        }

        if (title.isBlank()) {
            title = DEFAULT_DISPLAY_TITLE;
        }
        return truncate(title, maxLength);
    }

    private static String stripPrefix(Pattern pattern, String title) {
        Matcher matcher = pattern.matcher(title);
        if (matcher.matches()) {
            String stripped = matcher.group(1).trim();
            if (!stripped.isBlank()) {
                return stripped;
            }
        }
        return title;
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> truncate(value, MAX_LIST_ITEM_LENGTH))
                .distinct()
                .limit(MAX_LIST_ITEMS)
                .toList();
    }

    private List<String> collectAiChapterSourceChunkIds(AiChapterDraft chapterDraft, List<MaterialChunk> chunks) {
        List<String> sourceChunkIds = chapterDraft.topics().stream()
                .flatMap(topic -> safeList(topic.sourceChunkIds()).stream())
                .distinct()
                .toList();

        List<String> validIds = validSourceChunkIds(sourceChunkIds, chunks);
        if (!validIds.isEmpty()) {
            return validIds;
        }

        return chunks.stream()
                .map(chunk -> chunk.getChunkId().toString())
                .limit(5)
                .toList();
    }

    private List<String> validSourceChunkIds(List<String> sourceChunkIds, List<MaterialChunk> chunks) {
        List<String> validIds = chunks.stream()
                .map(chunk -> chunk.getChunkId().toString())
                .toList();

        return safeList(sourceChunkIds).stream()
                .filter(validIds::contains)
                .toList();
    }

    private String buildSummary(List<MaterialChunk> chunks) {
        return truncate(chunks.get(0).getContent(), 500);
    }

    private String extractTitle(String content, String fallback) {
        if (content == null || content.isBlank()) {
            return fallback;
        }

        String firstLine = content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(fallback);

        return truncate(firstLine, 90);
    }

    private List<String> extractKeyConcepts(List<MaterialChunk> chunks) {
        return chunks.stream()
                .flatMap(chunk -> List.of(extractTitle(chunk.getContent(), "Khái niệm chính")).stream())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<List<SyllabusSlot>> groupSyllabusSlots(List<SyllabusSlot> slots) {
        List<List<SyllabusSlot>> semanticGroups = new ArrayList<>();
        List<SyllabusSlot> currentGroup = new ArrayList<>();
        String currentModule = null;

        for (SyllabusSlot slot : slots) {
            String module = classifySyllabusModule(slot);
            boolean shouldStartNewGroup = currentModule != null
                    && (!currentModule.equals(module) || currentGroup.size() >= 4);

            if (shouldStartNewGroup) {
                semanticGroups.add(currentGroup);
                currentGroup = new ArrayList<>();
            }

            currentGroup.add(slot);
            currentModule = module;
        }

        if (!currentGroup.isEmpty()) {
            semanticGroups.add(currentGroup);
        }

        if (slots.size() >= 5 && semanticGroups.size() < 3) {
            return groupSyllabusSlotsBySize(slots, slots.size() >= 10 ? 3 : 2);
        }

        return semanticGroups;
    }

    private List<List<SyllabusSlot>> groupSyllabusSlotsBySize(List<SyllabusSlot> slots, int groupSize) {
        List<List<SyllabusSlot>> groups = new ArrayList<>();
        for (int i = 0; i < slots.size(); i += groupSize) {
            groups.add(slots.subList(i, Math.min(i + groupSize, slots.size())));
        }
        return groups;
    }

    private String classifySyllabusModule(SyllabusSlot slot) {
        String text = (slot.topic() + " " + String.join(" ", slot.details())).toLowerCase();
        if (containsAny(text, "html", "http", "web", "client-server", "client server")) {
            return "WEB_FOUNDATION";
        }
        if (containsAny(text, "css", "bootstrap", "responsive", "ui", "ux", "flexbox", "grid")) {
            return "UI";
        }
        if (containsAny(text, "javascript", "js", "es6", "dom")) {
            return "JAVASCRIPT";
        }
        if (containsAny(text, "node", "express", "backend", "api", "server")) {
            return "BACKEND";
        }
        if (containsAny(text, "mysql", "database", "sql", "cơ sở dữ liệu", "co so du lieu")) {
            return "DATABASE";
        }
        if (containsAny(text, "crud", "project", "lab", "update", "delete", "create")) {
            return "PROJECT";
        }
        return "GENERAL";
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String inferSyllabusChapterTitle(List<SyllabusSlot> slots) {
        String module = classifySyllabusModule(slots.get(0));
        return switch (module) {
            case "WEB_FOUNDATION" -> "Nền tảng Web và HTML";
            case "UI" -> "CSS và Responsive UI";
            case "JAVASCRIPT" -> "JavaScript phía Client";
            case "BACKEND" -> "Backend và API";
            case "DATABASE" -> "Database và dữ liệu";
            case "PROJECT" -> "CRUD và Project";
            default -> slots.size() == 1
                    ? slots.get(0).topic()
                    : "Module " + slots.get(0).slot() + "-" + slots.get(slots.size() - 1).slot();
        };
    }

    private String buildSyllabusChapterSummary(List<SyllabusSlot> slots) {
        String slotRange = slots.size() == 1
                ? "slot " + slots.get(0).slot()
                : "slot " + slots.get(0).slot() + "-" + slots.get(slots.size() - 1).slot();
        String topics = slots.stream()
                .map(SyllabusSlot::topic)
                .limit(3)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return "Học nhóm nội dung từ " + slotRange + ": " + topics + ".";
    }

    private String buildSyllabusTopicSummary(SyllabusSlot slot) {
        if (slot.details() == null || slot.details().isEmpty()) {
            return "Slot " + slot.slot() + ": " + slot.topic() + ".";
        }
        return "Slot " + slot.slot() + ": " + slot.topic() + ". Nội dung chính: " + String.join("; ", slot.details()) + ".";
    }

    private List<String> extractSyllabusConcepts(List<SyllabusSlot> slots) {
        return slots.stream()
                .map(SyllabusSlot::topic)
                .map(topic -> truncate(topic, 80))
                .distinct()
                .limit(5)
                .toList();
    }

    private int estimateSyllabusTopicMinutes(SyllabusSlot slot) {
        return slot.details() == null || slot.details().isEmpty() ? 20 : 30;
    }

    private boolean isBadSyllabusChapterTitle(String title) {
        String normalized = title == null ? "" : title.toLowerCase();
        return normalized.contains("syllabus")
                || normalized.contains("course description")
                || normalized.contains("assessment scheme")
                || normalized.contains("learning materials")
                || normalized.contains("prerequisite")
                || normalized.contains("credits");
    }

    private boolean isBadGenericChapterTitle(String title, DocumentKind kind) {
        String normalized = title == null ? "" : title.toLowerCase();
        if (kind == DocumentKind.ASSIGNMENT) {
            return normalized.equals("assignment") || normalized.equals("requirements") || normalized.equals("bài tập");
        }
        if (kind == DocumentKind.SLIDE_DECK) {
            return normalized.matches("slide\\s*\\d+");
        }
        return false;
    }

    private String fallbackAiModel(DocumentAnalysis analysis) {
        return switch (analysis.kind()) {
            case SYLLABUS -> "rule-based-syllabus-mvp";
            case LECTURE_NOTE -> "rule-based-lecture-note-mvp";
            case SLIDE_DECK -> "rule-based-slide-deck-mvp";
            case ASSIGNMENT -> "rule-based-assignment-mvp";
            case GENERAL -> "rule-based-mvp";
        };
    }

    private String fallbackWarning(DocumentAnalysis analysis) {
        return switch (analysis.kind()) {
            case SYLLABUS -> "AI trả cấu trúc syllabus chưa đạt, đã dùng rule-based syllabus fallback";
            case LECTURE_NOTE -> "AI trả cấu trúc lecture note chưa đạt, đã dùng heading fallback";
            case SLIDE_DECK -> "AI trả cấu trúc slide chưa đạt, đã dùng section fallback";
            case ASSIGNMENT -> "AI trả cấu trúc bài tập chưa đạt, đã dùng rule-based fallback";
            case GENERAL -> "AI chưa sẵn sàng hoặc dữ liệu AI không hợp lệ, đã dùng rule-based fallback";
        };
    }

    private List<String> toChunkIds(List<MaterialChunk> chunks) {
        return chunks.stream()
                .map(chunk -> chunk.getChunkId().toString())
                .distinct()
                .toList();
    }

    private List<HeadingSection> detectHeadingSections(List<MaterialChunk> chunks) {
        List<HeadingSection> sections = new ArrayList<>();
        HeadingSection currentSection = null;
        boolean hasHeading = false;

        for (MaterialChunk chunk : chunks) {
            List<String> lines = chunk.getContent() == null
                    ? List.of()
                    : chunk.getContent().lines().toList();

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isBlank()) {
                    continue;
                }

                Heading heading = parseHeading(trimmed);
                if (heading != null) {
                    hasHeading = true;
                    currentSection = new HeadingSection(heading.level(), heading.title());
                    currentSection.addChunk(chunk);
                    sections.add(currentSection);
                    continue;
                }

                if (currentSection != null) {
                    currentSection.append(trimmed);
                    currentSection.addChunk(chunk);
                }
            }
        }

        return hasHeading ? sections : List.of();
    }

    private Heading parseHeading(String line) {
        if (LIST_MARKER_PATTERN.matcher(line).matches() || line.contains("|")) {
            return null;
        }

        Matcher markdownMatcher = MARKDOWN_HEADING_PATTERN.matcher(line);
        if (markdownMatcher.matches()) {
            String title = markdownMatcher.group(2).trim();
            return isIgnorableHeadingTitle(title) ? null : new Heading(markdownMatcher.group(1).length(), title);
        }

        Matcher numberedMatcher = NUMBERED_HEADING_PATTERN.matcher(line);
        if (numberedMatcher.matches()) {
            String outlineNumber = numberedMatcher.group(1);
            String title = numberedMatcher.group(2).trim();
            if (!isLikelyNumberedHeading(outlineNumber, title) || isIgnorableHeadingTitle(title)) {
                return null;
            }
            int level = outlineNumber.split("\\.").length;
            return new Heading(level, title);
        }

        return null;
    }

    // A heading title that carries no learning value on its own: clock times, time ranges,
    // bare numbers (e.g. "00"), or fragments too short to be a real section title.
    static boolean isIgnorableHeadingTitle(String title) {
        if (title == null) {
            return true;
        }
        String trimmed = title.trim();
        if (trimmed.length() < 3) {
            return true;
        }
        if (TIME_RANGE_PATTERN.matcher(trimmed).matches() || TIME_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.matches("\\d{1,4}");
    }

    private boolean isLikelyNumberedHeading(String outlineNumber, String title) {
        int level = outlineNumber.split("\\.").length;
        if (level > 1) {
            return true;
        }

        int wordCount = title.split("\\s+").length;
        return title.length() <= 90
                && wordCount <= 10
                && !title.endsWith(".")
                && !title.endsWith(",")
                && !title.endsWith(";")
                && !title.endsWith(":");
    }

    private String collectChapterText(ChapterDraft draft) {
        StringBuilder text = new StringBuilder(draft.chapterSection().text());
        for (HeadingSection topicSection : draft.topicSections()) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            text.append(topicSection.title()).append(System.lineSeparator()).append(topicSection.text());
        }
        return text.toString().trim();
    }

    private List<MaterialChunk> collectChapterChunks(ChapterDraft draft) {
        List<MaterialChunk> chunks = new ArrayList<>(draft.chapterSection().chunks());
        for (HeadingSection topicSection : draft.topicSections()) {
            for (MaterialChunk chunk : topicSection.chunks()) {
                if (!chunks.contains(chunk)) {
                    chunks.add(chunk);
                }
            }
        }
        return chunks;
    }

    private int estimateMinutes(List<MaterialChunk> chunks, int minimumMinutes) {
        int tokenCount = chunks.stream()
                .map(MaterialChunk::getTokenCount)
                .filter(token -> token != null)
                .mapToInt(Integer::intValue)
                .sum();

        return Math.max(minimumMinutes, tokenCount / 8);
    }

    private DifficultyLevel resolveDifficulty(int chapterIndex) {
        if (chapterIndex == 0) {
            return DifficultyLevel.EASY;
        }
        if (chapterIndex == 1) {
            return DifficultyLevel.MEDIUM;
        }
        return DifficultyLevel.HARD;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength - 3).trim() + "...";
    }

    private record Heading(int level, String title) {
    }

    // Learning phases used to regroup flat technical-report sections into meaningful chapters.
    enum ReportPhase {
        OVERVIEW("Tổng quan vấn đề"),
        ROOT_CAUSE("Nguyên nhân và tác động"),
        FIX("Cách khắc phục"),
        VALIDATION("Kiểm thử và xác minh"),
        RESULT("Kết quả cuối cùng");

        private final String chapterTitle;

        ReportPhase(String chapterTitle) {
            this.chapterTitle = chapterTitle;
        }

        String chapterTitle() {
            return chapterTitle;
        }
    }

    private record GeneratedStructure(List<Chapter> chapters, List<Topic> topics) {
    }

    private record ChapterDraft(HeadingSection chapterSection, List<HeadingSection> topicSections) {

        ChapterDraft(HeadingSection chapterSection) {
            this(chapterSection, new ArrayList<>());
        }
    }

    private static class HeadingSection {

        private final int level;
        private final String title;
        private final StringBuilder text = new StringBuilder();
        private final List<MaterialChunk> chunks = new ArrayList<>();

        HeadingSection(int level, String title) {
            this.level = level;
            this.title = title;
        }

        int level() {
            return level;
        }

        String title() {
            return title;
        }

        String text() {
            return text.isEmpty() ? title : text.toString().trim();
        }

        List<MaterialChunk> chunks() {
            return chunks;
        }

        void append(String line) {
            if (!text.isEmpty()) {
                text.append(System.lineSeparator());
            }
            text.append(line);
        }

        void addChunk(MaterialChunk chunk) {
            if (!chunks.contains(chunk)) {
                chunks.add(chunk);
            }
        }
    }
}
