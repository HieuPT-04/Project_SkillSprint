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
import com.skillsprint.mapper.LearningStructureMapper;
import com.skillsprint.repository.ChapterRepository;
import com.skillsprint.repository.LearningStructureVersionRepository;
import com.skillsprint.repository.MaterialChunkRepository;
import com.skillsprint.repository.StudyWorkspaceRepository;
import com.skillsprint.repository.TopicRepository;
import com.skillsprint.service.learningstructure.LearningStructureService.ReportPhase;
import com.skillsprint.service.learningstructure.ai.GeminiLearningStructureClient;
import com.skillsprint.service.subscription.QuotaService;
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
            "1. Tổng quan          | Tổng quan",
            "1) Tổng quan          | Tổng quan",
            "1.1. Backend          | Backend",
            "2 - Lỗi thường gặp    | Lỗi thường gặp",
            "3: Kết luận           | Kết luận",
            "4/ Tóm tắt            | Tóm tắt",
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
                The planner used only the first selected time slot.
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
                "Tổng quan",
                "Nguyên nhân gốc",
                "Cách sửa",
                "Kiểm thử đã bổ sung",
                "Kết quả cuối cùng"
        );
        assertThat(topicTitles).doesNotContain("Summary", "Root Cause", "Fix Implemented");
    }
}
