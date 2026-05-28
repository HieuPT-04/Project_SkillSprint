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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LearningStructureService {

    static int TOPICS_PER_CHAPTER = 3;
    static Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,5})\\s+(.{3,160})$");
    static Pattern NUMBERED_HEADING_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+){0,4})[.)]?\\s+(.{3,160})$");
    static Pattern LIST_MARKER_PATTERN = Pattern.compile("^[-*•]\\s+.+$");

    StudyWorkspaceRepository workspaceRepository;
    MaterialChunkRepository materialChunkRepository;
    LearningStructureVersionRepository structureVersionRepository;
    ChapterRepository chapterRepository;
    TopicRepository topicRepository;
    LearningStructureMapper learningStructureMapper;
    GeminiLearningStructureClient geminiLearningStructureClient;

    @Transactional
    public LearningStructureResponse generate(String userId, UUID workspaceId) {
        StudyWorkspace workspace = findOwnedWorkspace(userId, workspaceId);
        List<MaterialChunk> chunks = materialChunkRepository
                .findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(workspaceId);

        if (chunks.isEmpty()) {
            throw new AppException(ErrorCode.MATERIAL_CHUNKS_NOT_READY);
        }

        AiLearningStructureDraft aiDraft = geminiLearningStructureClient.generate(chunks);
        boolean useAi = isValidAiDraft(aiDraft, chunks);

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
            structureVersion.setAiModel("rule-based-mvp");
            structureVersion.setConfidenceScore(BigDecimal.valueOf(0.70));
            structureVersion.setWarnings(List.of("AI chưa sẵn sàng hoặc dữ liệu AI không hợp lệ, đã dùng rule-based fallback"));
        }

        LearningStructureVersion savedVersion = structureVersionRepository.saveAndFlush(structureVersion);
        GeneratedStructure generatedStructure;
        if (useAi) {
            generatedStructure = createAiStructure(savedVersion, workspace, aiDraft, chunks);
        } else {
            List<HeadingSection> headingSections = detectHeadingSections(chunks);
            generatedStructure = headingSections.isEmpty()
                    ? createFallbackStructure(savedVersion, workspace, chunks)
                    : createHeadingBasedStructure(savedVersion, workspace, headingSections);
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
            chapter.setTitle(truncate(defaultText(draft.title(), "Chương " + (i + 1)), 90));
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
                topic.setTitle(truncate(defaultText(draft.title(), "Topic " + (topicIndex + 1)), 90));
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
            chapter.setTitle(truncate(section.title(), 90));
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
                topic.setTitle(truncate(section.title(), 90));
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

    private boolean isValidAiDraft(AiLearningStructureDraft draft, List<MaterialChunk> chunks) {
        if (draft == null || chunks == null || chunks.isEmpty()) {
            return false;
        }
        if (draft.chapters() == null || draft.chapters().isEmpty()) {
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

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(10)
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
            return new Heading(markdownMatcher.group(1).length(), markdownMatcher.group(2).trim());
        }

        Matcher numberedMatcher = NUMBERED_HEADING_PATTERN.matcher(line);
        if (numberedMatcher.matches()) {
            String outlineNumber = numberedMatcher.group(1);
            String title = numberedMatcher.group(2).trim();
            if (!isLikelyNumberedHeading(outlineNumber, title)) {
                return null;
            }
            int level = outlineNumber.split("\\.").length;
            return new Heading(level, title);
        }

        return null;
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

    private String truncate(String value, int maxLength) {
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
