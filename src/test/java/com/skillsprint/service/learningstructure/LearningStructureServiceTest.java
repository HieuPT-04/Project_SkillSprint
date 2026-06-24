package com.skillsprint.service.learningstructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.entity.Chapter;
import com.skillsprint.entity.LearningStructureVersion;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.StudyWorkspace;
import com.skillsprint.entity.Topic;
import com.skillsprint.enums.learningstructure.GeneratedBy;
import com.skillsprint.mapper.LearningStructureMapper;
import com.skillsprint.repository.ChapterRepository;
import com.skillsprint.repository.LearningStructureVersionRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.TopicRepository;
import com.skillsprint.service.learningstructure.LearningStructureService.ReportPhase;
import com.skillsprint.service.learningstructure.ai.AiChapterDraft;
import com.skillsprint.service.learningstructure.ai.AiLearningStructureDraft;
import com.skillsprint.service.learningstructure.ai.AiTopicDraft;
import com.skillsprint.service.learningstructure.ai.GeminiLearningStructureClient;
import com.skillsprint.service.subscription.QuotaService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class LearningStructureServiceTest {

    private static final int MAX_LENGTH = 90;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Tổng quan | tong quan",
            "Lỗi 1: Calendar chỉ dùng time slot đầu tiên | loi 1: calendar chi dung time slot dau tien",
            "Cách sửa | cach sua",
            "Kết luận | ket luan",
            "  Prompt   Gemini   Calendar  | prompt gemini calendar",
    })
    void normalizeForMatchRemovesVietnameseDiacriticsAndCollapsesWhitespace(String input, String expected) {
        assertThat(LearningStructureService.normalizeForMatch(input)).isEqualTo(expected);
    }

    @Test
    void normalizeForMatchHandlesNullSafely() {
        assertThat(LearningStructureService.normalizeForMatch(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "1. Tổng quan          | Tổng quan",
            "1) Tổng quan          | Tổng quan",
            "1.1. Backend          | Backend",
            "2 - Lỗi thường gặp    | Lỗi thường gặp",
            "3: Kết luận           | Kết luận",
            "4/ Tóm tắt            | Tóm tắt",
            "6.1 ScheduleConfig now keeps all time windows | ScheduleConfig now keeps all time windows",
            "Bước 1: 1. Tổng quan  | Tổng quan",
            "Step 2 - 2. Backend   | Backend",
    })
    void cleanDisplayTitleStripsRawNumberingPrefixes(String input, String expected) {
        assertThat(LearningStructureService.cleanDisplayTitle(input, MAX_LENGTH))
                .isEqualTo(expected.trim());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026 Roadmap",
            "404 Error Handling",
            "5 Whys",
            "Java 17 Basics",
            "10x Productivity",
    })
    void cleanDisplayTitleKeepsMeaningfulNumericTitles(String input) {
        assertThat(LearningStructureService.cleanDisplayTitle(input, MAX_LENGTH))
                .isEqualTo(input);
    }

    @Test
    void cleanDisplayTitleNormalizesWhitespace() {
        assertThat(LearningStructureService.cleanDisplayTitle("  Tổng    quan   ", MAX_LENGTH))
                .isEqualTo("Tổng quan");
    }

    @Test
    void cleanDisplayTitleFallsBackToDefaultWhenBlank() {
        assertThat(LearningStructureService.cleanDisplayTitle("   ", MAX_LENGTH))
                .isEqualTo("Nội dung học");
        assertThat(LearningStructureService.cleanDisplayTitle(null, MAX_LENGTH))
                .isEqualTo("Nội dung học");
    }

    @Test
    void cleanDisplayTitleKeepsBareNumberWithoutSeparator() {
        // No content after the separator -> nothing to strip, keep as-is.
        assertThat(LearningStructureService.cleanDisplayTitle("1.", MAX_LENGTH))
                .isEqualTo("1.");
    }

    @Test
    void cleanDisplayTitleTruncatesLongTitles() {
        String longTitle = "A".repeat(120);
        String result = LearningStructureService.cleanDisplayTitle(longTitle, MAX_LENGTH);
        assertThat(result).hasSize(MAX_LENGTH);
        assertThat(result).endsWith("...");
    }

    // Regression: clock times / time ranges must never be corrupted into "00" by prefix cleanup.
    @ParameterizedTest
    @ValueSource(strings = {
            "08:00 - 10:00",
            "14:00 - 16:00",
            "10:00",
            "09:30:00",
            "2026-06-24",
    })
    void cleanDisplayTitleDoesNotCorruptTimeOrDateValues(String input) {
        String result = LearningStructureService.cleanDisplayTitle(input, MAX_LENGTH);
        assertThat(result).isEqualTo(input);
        assertThat(result).isNotEqualTo("00");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "08:00 - 10:00",
            "14:00 - 16:00",
            "10:00",
            "00",
            "12",
            "  ",
    })
    void timeRangesAndBareNumbersAreNotTreatedAsHeadings(String title) {
        assertThat(LearningStructureService.isIgnorableHeadingTitle(title)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Summary",
            "Root Cause",
            "Fix Implemented",
            "Tổng quan lỗi Study Calendar",
    })
    void realSectionTitlesAreNotIgnorableHeadings(String title) {
        assertThat(LearningStructureService.isIgnorableHeadingTitle(title)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Summary                | Tổng quan",
            "Affected Area          | Phạm vi ảnh hưởng",
            "Root Cause             | Nguyên nhân gốc",
            "Impact                 | Tác động",
            "Fix Implemented        | Cách sửa",
            "Expected Behavior      | Hành vi mong đợi",
            "Tests Added / Updated  | Kiểm thử đã bổ sung",
            "Verification           | Xác minh",
            "Final Result           | Kết quả cuối cùng",
    })
    void localizeReportHeadingMapsEnglishSectionsToVietnamese(String input, String expected) {
        assertThat(LearningStructureService.localizeReportHeading(input.trim())).isEqualTo(expected.trim());
    }

    @Test
    void localizeReportHeadingKeepsUnknownTitlesUnchanged() {
        assertThat(LearningStructureService.localizeReportHeading("Custom Domain Logic"))
                .isEqualTo("Custom Domain Logic");
    }

    @Test
    void classifyReportPhaseGroupsSectionsByLearningPhase() {
        assertThat(LearningStructureService.classifyReportPhase("Summary")).isEqualTo(ReportPhase.OVERVIEW);
        assertThat(LearningStructureService.classifyReportPhase("Affected Area")).isEqualTo(ReportPhase.OVERVIEW);
        assertThat(LearningStructureService.classifyReportPhase("Root Cause")).isEqualTo(ReportPhase.ROOT_CAUSE);
        assertThat(LearningStructureService.classifyReportPhase("Impact")).isEqualTo(ReportPhase.ROOT_CAUSE);
        assertThat(LearningStructureService.classifyReportPhase("Fix Implemented")).isEqualTo(ReportPhase.FIX);
        assertThat(LearningStructureService.classifyReportPhase("Expected Behavior")).isEqualTo(ReportPhase.FIX);
        assertThat(LearningStructureService.classifyReportPhase("Tests Added / Updated")).isEqualTo(ReportPhase.VALIDATION);
        assertThat(LearningStructureService.classifyReportPhase("Verification")).isEqualTo(ReportPhase.VALIDATION);
        assertThat(LearningStructureService.classifyReportPhase("Final Result")).isEqualTo(ReportPhase.RESULT);
        assertThat(LearningStructureService.classifyReportPhase("Random heading")).isNull();
    }

    @Test
    void classifyReportPhaseDetectsVietnameseAccentedReportHeadings() {
        assertThat(LearningStructureService.classifyReportPhase("Tổng quan")).isEqualTo(ReportPhase.OVERVIEW);
        assertThat(LearningStructureService.classifyReportPhase("Lỗi 1: Calendar chỉ dùng time slot đầu tiên"))
                .isEqualTo(ReportPhase.ROOT_CAUSE);
        assertThat(LearningStructureService.classifyReportPhase("Vấn đề")).isEqualTo(ReportPhase.ROOT_CAUSE);
        assertThat(LearningStructureService.classifyReportPhase("Cách sửa")).isEqualTo(ReportPhase.FIX);
        assertThat(LearningStructureService.classifyReportPhase("Prompt Gemini Calendar")).isEqualTo(ReportPhase.AI_CONTROL);
        assertThat(LearningStructureService.classifyReportPhase("Gemini request config")).isEqualTo(ReportPhase.AI_CONTROL);
        assertThat(LearningStructureService.classifyReportPhase("Tests đã thêm/cập nhật")).isEqualTo(ReportPhase.VALIDATION);
        assertThat(LearningStructureService.classifyReportPhase("Build / Test result")).isEqualTo(ReportPhase.VALIDATION);
        assertThat(LearningStructureService.classifyReportPhase("Kết luận")).isEqualTo(ReportPhase.RESULT);
    }

    @Test
    void classifyReportPhaseDetectsNoAccentReportHeadings() {
        assertThat(LearningStructureService.classifyReportPhase("Tong quan")).isEqualTo(ReportPhase.OVERVIEW);
        assertThat(LearningStructureService.classifyReportPhase("Loi 1")).isEqualTo(ReportPhase.ROOT_CAUSE);
        assertThat(LearningStructureService.classifyReportPhase("Van de")).isEqualTo(ReportPhase.ROOT_CAUSE);
        assertThat(LearningStructureService.classifyReportPhase("Cach sua")).isEqualTo(ReportPhase.FIX);
        assertThat(LearningStructureService.classifyReportPhase("Ket luan")).isEqualTo(ReportPhase.RESULT);
    }

    @Test
    void classifyReportPhaseStillDetectsEnglishReportHeadings() {
        assertThat(LearningStructureService.classifyReportPhase("Summary")).isEqualTo(ReportPhase.OVERVIEW);
        assertThat(LearningStructureService.classifyReportPhase("Root Cause")).isEqualTo(ReportPhase.ROOT_CAUSE);
        assertThat(LearningStructureService.classifyReportPhase("Fix Implemented")).isEqualTo(ReportPhase.FIX);
        assertThat(LearningStructureService.classifyReportPhase("Verification")).isEqualTo(ReportPhase.VALIDATION);
        assertThat(LearningStructureService.classifyReportPhase("Final Result")).isEqualTo(ReportPhase.RESULT);
    }

    @Test
    void isReportLikeHeadingsDetectsTechnicalReports() {
        List<String> reportHeadings = List.of(
                "Summary",
                "Affected Area",
                "Root Cause",
                "Impact",
                "Fix Implemented",
                "Expected Behavior",
                "Tests Added / Updated",
                "Verification",
                "Final Result"
        );
        assertThat(LearningStructureService.isReportLikeHeadings(reportHeadings)).isTrue();
    }

    @Test
    void isReportLikeHeadingsRejectsOrdinaryLessonHeadings() {
        List<String> lessonHeadings = List.of(
                "Spring Boot",
                "Controller",
                "Service",
                "Repository"
        );
        assertThat(LearningStructureService.isReportLikeHeadings(lessonHeadings)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallbackForTechnicalReportProducesGroupedVietnameseChaptersWithoutDuplicatedNumbering() {
        StudyWorkspaceRepository workspaceRepository = mock(StudyWorkspaceRepository.class);
        MaterialChunkRepository materialChunkRepository = mock(MaterialChunkRepository.class);
        LearningStructureVersionRepository structureVersionRepository = mock(LearningStructureVersionRepository.class);
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        LearningStructureMapper mapper = mock(LearningStructureMapper.class);
        GeminiLearningStructureClient geminiClient = mock(GeminiLearningStructureClient.class);
        QuotaService quotaService = mock(QuotaService.class);

        LearningStructureService service = new LearningStructureService(
                workspaceRepository,
                materialChunkRepository,
                structureVersionRepository,
                chapterRepository,
                topicRepository,
                mapper,
                geminiClient,
                quotaService
        );

        MaterialChunk chunk = new MaterialChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setContent("""
                ## Summary
                The study calendar ignores onboarding time slots.
                ## Affected Area
                Onboarding flow and calendar generation.
                ## Root Cause
                The planner used only the first selected slot and then ignored selected slots.
                ## Impact
                Users received a schedule that ignored their availability.
                ## Fix Implemented
                Parse all selected time windows and place sessions per window.
                ## Expected Behavior
                Each session lands inside a selected window such as 08:00 - 10:00.
                ## Tests Added / Updated
                Added planner unit tests for multi-window availability.
                ## Verification
                Ran the regression suite.
                ## Final Result
                All tests pass.
                """);

        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(any(), any(), any()))
                .thenReturn(Optional.of(new StudyWorkspace()));
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(any()))
                .thenReturn(List.of(chunk));
        // Force the rule-based fallback path (AI returns nothing).
        when(geminiClient.generate(any(), any())).thenReturn(null);
        when(structureVersionRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(any()))
                .thenReturn(Optional.empty());
        when(structureVersionRepository.saveAndFlush(any(LearningStructureVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(topicRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(), any(), any())).thenReturn(null);

        service.generate("user-1", UUID.randomUUID());

        ArgumentCaptor<List<Chapter>> chapterCaptor = ArgumentCaptor.forClass(List.class);
        verify(chapterRepository).saveAllAndFlush(chapterCaptor.capture());
        List<String> chapterTitles = chapterCaptor.getValue().stream().map(Chapter::getTitle).toList();

        // Grouped into learner phases, not 9 flat copied headings under one chapter.
        assertThat(chapterTitles).containsExactly(
                "Tổng quan vấn đề",
                "Nguyên nhân và tác động",
                "Cách khắc phục",
                "Kiểm thử và xác minh",
                "Kết quả cuối cùng"
        );
        assertThat(chapterTitles).doesNotContain("00");

        ArgumentCaptor<List<Topic>> topicCaptor = ArgumentCaptor.forClass(List.class);
        verify(topicRepository).saveAllAndFlush(topicCaptor.capture());
        List<String> topicTitles = topicCaptor.getValue().stream().map(Topic::getTitle).toList();

        // Topic titles localized to Vietnamese, no "00" and no copied English headings.
        assertThat(topicTitles).doesNotContain("00");
        assertThat(topicTitles).contains(
                "Bối cảnh và phạm vi sửa lỗi",
                "Phân tích vấn đề và nguyên nhân",
                "Cách khắc phục chính",
                "Kiểm thử đã bổ sung",
                "Kết luận và kết quả"
        );
        assertThat(topicTitles).doesNotContain("Summary", "Root Cause", "Fix Implemented");
        assertThat(topicTitles).doesNotContain("selected slot", "selected slots");
        assertThat(topicTitles).doesNotContain("Vấn đề", "Cách sửa", "File liên quan");
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsOneHugeAiReportDraftAndRegroupsVietnameseReportFallback() {
        StudyWorkspaceRepository workspaceRepository = mock(StudyWorkspaceRepository.class);
        MaterialChunkRepository materialChunkRepository = mock(MaterialChunkRepository.class);
        LearningStructureVersionRepository structureVersionRepository = mock(LearningStructureVersionRepository.class);
        ChapterRepository chapterRepository = mock(ChapterRepository.class);
        TopicRepository topicRepository = mock(TopicRepository.class);
        LearningStructureMapper mapper = mock(LearningStructureMapper.class);
        GeminiLearningStructureClient geminiClient = mock(GeminiLearningStructureClient.class);
        QuotaService quotaService = mock(QuotaService.class);

        LearningStructureService service = new LearningStructureService(
                workspaceRepository,
                materialChunkRepository,
                structureVersionRepository,
                chapterRepository,
                topicRepository,
                mapper,
                geminiClient,
                quotaService
        );

        MaterialChunk chunk = new MaterialChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setTokenCount(2000);
        chunk.setContent("""
                # Báo cáo sửa lỗi Calendar AI / Study Calendar
                1. Tổng quan
                Sửa các lỗi liên quan đến planner và Gemini calendar.
                2. Lỗi 1: Calendar chỉ dùng time slot đầu tiên
                Vấn đề
                Planner bỏ qua selected slot và selected slots trong các khung giờ còn lại.
                Cách sửa
                Parse toàn bộ selected time windows, bao gồm 08:00 - 10:00.
                File liên quan
                CalendarPlanner.java
                Thay đổi chính
                Rule-based planner đặt session theo nhiều time windows.
                3. Lỗi 2: AI có thể sinh lịch ngoài availability
                Reject lịch ngoài availability.
                4. Lỗi 3: Gemini hết token hoặc trả JSON cụt
                Fallback khi AI lỗi
                Quay về rule-based plan an toàn.
                Prompt Gemini Calendar
                Yêu cầu Gemini trả JSON đúng schema.
                Gemini request config
                Tăng giới hạn token và cấu hình response.
                Tests đã thêm/cập nhật
                Thêm test cho multi-window availability.
                Build / Test result
                mvn test pass.
                Kết luận
                Study Calendar tạo lịch đúng availability.
                """);

        AiLearningStructureDraft oneHugeAiDraft = new AiLearningStructureDraft(
                BigDecimal.valueOf(0.90),
                List.of(),
                List.of(new AiChapterDraft(
                        "Báo cáo sửa lỗi Calendar AI / Study Calendar",
                        "Flat report outline",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        120,
                        List.of(
                                aiTopic("1.1 Tổng quan"),
                                aiTopic("1.2 Lỗi 1: Calendar chỉ dùng time slot đầu tiên"),
                                aiTopic("1.3 Vấn đề"),
                                aiTopic("1.4 Cách sửa"),
                                aiTopic("1.5 File liên quan"),
                                aiTopic("1.6 Thay đổi chính"),
                                aiTopic("1.7 Lỗi 2: AI có thể sinh lịch ngoài availability"),
                                aiTopic("1.8 Lỗi 3: Gemini hết token hoặc trả JSON cụt"),
                                aiTopic("1.9 Fallback khi AI lỗi"),
                                aiTopic("1.10 Prompt Gemini Calendar"),
                                aiTopic("1.11 Gemini request config"),
                                aiTopic("1.12 Tests đã thêm/cập nhật"),
                                aiTopic("1.13 Build / Test result"),
                                aiTopic("1.14 Kết luận"),
                                aiTopic("1.15 selected slot"),
                                aiTopic("1.16 selected slots"),
                                aiTopic("1.17 08:00 - 10:00"),
                                aiTopic("1.18 14:00 - 16:00"),
                                aiTopic("1.19 Files changed"),
                                aiTopic("1.20 Expected Behavior"),
                                aiTopic("1.21 Verification")
                        )
                ))
        );
        assertThat(oneHugeAiDraft.chapters().get(0).topics()).hasSizeGreaterThan(20);

        when(workspaceRepository.findByWorkspaceIdAndUserUserIdAndStatusNot(any(), any(), any()))
                .thenReturn(Optional.of(new StudyWorkspace()));
        when(materialChunkRepository.findByWorkspaceWorkspaceIdOrderByCreatedAtAscChunkIndexAsc(any()))
                .thenReturn(List.of(chunk));
        when(geminiClient.generate(any(), any())).thenReturn(oneHugeAiDraft);
        when(structureVersionRepository.findTopByWorkspaceWorkspaceIdOrderByVersionNoDesc(any()))
                .thenReturn(Optional.empty());
        when(structureVersionRepository.saveAndFlush(any(LearningStructureVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(chapterRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(topicRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(), any(), any())).thenReturn(null);

        service.generate("user-1", UUID.randomUUID());

        ArgumentCaptor<LearningStructureVersion> versionCaptor = ArgumentCaptor.forClass(LearningStructureVersion.class);
        verify(structureVersionRepository).saveAndFlush(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getGeneratedBy()).isEqualTo(GeneratedBy.SYSTEM);

        ArgumentCaptor<List<Chapter>> chapterCaptor = ArgumentCaptor.forClass(List.class);
        verify(chapterRepository).saveAllAndFlush(chapterCaptor.capture());
        List<String> chapterTitles = chapterCaptor.getValue().stream().map(Chapter::getTitle).toList();

        assertThat(chapterTitles).hasSizeBetween(3, 6);
        assertThat(chapterTitles).contains(
                "Tổng quan vấn đề",
                "Nguyên nhân và tác động",
                "Cách khắc phục",
                "Kiểm soát AI và fallback",
                "Kiểm thử và xác minh"
        );
        assertThat(chapterTitles).doesNotContain(
                "Tong quan van de",
                "Nguyen nhan va tac dong",
                "Cach khac phuc",
                "Kiem soat AI va fallback",
                "Kiem thu va xac minh"
        );
        assertThat(chapterTitles)
                .doesNotContain("Báo cáo sửa lỗi Calendar AI / Study Calendar");

        ArgumentCaptor<List<Topic>> topicCaptor = ArgumentCaptor.forClass(List.class);
        verify(topicRepository).saveAllAndFlush(topicCaptor.capture());
        List<Topic> savedTopics = topicCaptor.getValue();
        List<String> topicTitles = savedTopics.stream().map(Topic::getTitle).toList();

        assertThat(savedTopics).hasSizeLessThan(20);
        assertThat(savedTopics.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        topic -> topic.getChapter().getTitle(),
                        java.util.stream.Collectors.counting()
                ))
                .values())
                .allMatch(count -> count <= 5);
        assertThat(topicTitles).doesNotContain("Vấn đề", "Cách sửa", "File liên quan");
        assertThat(topicTitles).doesNotContain("selected slot", "selected slots", "00");
        assertThat(topicTitles).noneMatch(title -> title.matches("^\\d+(?:\\.\\d+)*[.)]?\\s+.*"));
        assertThat(topicTitles).contains("Lỗi 1: Calendar chỉ dùng time slot đầu tiên");
        assertThat(savedTopics.stream().map(Topic::getSummaryContent).toList())
                .anyMatch(summary -> summary.contains("08:00 - 10:00"));
    }

    private static AiTopicDraft aiTopic(String title) {
        return new AiTopicDraft(
                title,
                title,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                15,
                List.of()
        );
    }
}
