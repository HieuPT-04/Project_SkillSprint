package com.skillsprint.service.learningstructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentAnalysis;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentKind;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LearningDocumentAnalyzerTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Tổng quan | tong quan",
            "Lỗi 1: Calendar chỉ dùng time slot đầu tiên | loi 1: calendar chi dung time slot dau tien",
            "Cách sửa | cach sua",
            "Kết luận | ket luan",
            "  Tests   đã   thêm/cập nhật  | tests da them/cap nhat",
    })
    void normalizeForMatchingRemovesVietnameseDiacriticsAndCollapsesWhitespace(
            String input,
            String expected
    ) {
        assertThat(LearningDocumentAnalyzer.normalizeForMatching(input)).isEqualTo(expected);
    }

    @Test
    void normalizeForMatchingHandlesNullSafely() {
        assertThat(LearningDocumentAnalyzer.normalizeForMatching(null)).isEmpty();
    }

    @Test
    void analyzeDetectsSyllabusSlotsFromScheduleTable() {
        MaterialChunk chunk = chunk("""
                Syllabus Name: PRW301 - Phát triển Ứng dụng Web
                Session Schedule
                Slot | Chủ đề bài học | Hình thức | Nội dung chi tiết | Bài tập / Chuẩn bị
                1 | Tổng quan về Web & HTML5 | Lớp học | Giới thiệu Client-Server, giao thức HTTP | Đọc chương 1
                2 | Định kiểu giao diện với CSS3 | Lớp học | CSS Selector, Box Model, Flexbox | Bài tập Lab 1
                3 | Responsive Design & Bootstrap | Lớp học | Media Queries và Grid System | Bài tập Lab 2
                4 | Cơ bản về JavaScript ES6+ | Lớp học | Biến, hàm, array, object | Chuẩn bị lab
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.SYLLABUS);
        assertThat(analysis.syllabusSlots()).hasSize(4);
        assertThat(analysis.syllabusSlots().get(0).slot()).isEqualTo(1);
        assertThat(analysis.syllabusSlots().get(0).topic()).isEqualTo("Tổng quan về Web & HTML5");
        assertThat(analysis.syllabusSlots().get(0).details())
                .contains("Giới thiệu Client-Server, giao thức HTTP");
    }

    @Test
    void analyzeDetectsLectureNoteFromHeadings() {
        MaterialChunk chunk = chunk("""
                # Spring Boot
                Spring Boot giúp xây dựng REST API nhanh hơn.
                ## Controller
                Controller nhận request và trả response.
                ## Service
                Service xử lý business logic.
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.LECTURE_NOTE);
        assertThat(analysis.sections()).hasSize(3);
    }

    @Test
    void analyzeDetectsSlideDeckFromSlideLabels() {
        MaterialChunk chunk = chunk("""
                Slide 1: Introduction
                What is HTTP?
                Slide 2: Request Response
                Client sends request, server returns response.
                Slide 3: REST API
                REST API uses resources and HTTP methods.
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.SLIDE_DECK);
        assertThat(analysis.signals()).contains("slideSignals");
    }

    @Test
    void analyzeDetectsAssignmentFromRequirementSignals() {
        MaterialChunk chunk = chunk("""
                Assignment: Build a personal landing page
                Requirements:
                - Create responsive layout
                - Submit source code before deadline
                Deliverables: GitHub link and demo video
                Rubric: UI quality, code structure, and deployment
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.ASSIGNMENT);
        assertThat(analysis.signals()).contains("assignmentKeywords");
    }

    @Test
    void analyzeKeepsGeneralDocumentWhenThereIsNoStrongSignal() {
        MaterialChunk chunk = chunk("""
                REST API là cách thiết kế endpoint theo resource.
                Client gửi request và server trả response JSON.
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.GENERAL);
        assertThat(analysis.syllabusSlots()).isEmpty();
    }

    @Test
    void analyzeDetectsVietnameseTechnicalReportHeadings() {
        MaterialChunk chunk = chunk("""
                # Báo cáo sửa lỗi Calendar AI / Study Calendar
                1. Tổng quan
                2. Lỗi 1: Calendar chỉ dùng time slot đầu tiên
                Vấn đề
                Cách sửa
                3. Lỗi 2: AI có thể sinh lịch ngoài availability
                Fallback khi AI lỗi
                Prompt Gemini Calendar
                Gemini request config
                Tests đã thêm/cập nhật
                Build / Test result
                Kết luận
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.LECTURE_NOTE);
        assertThat(analysis.signals()).contains("technicalReportHeadings");
        assertThat(analysis.sections().stream().map(section -> section.title()).toList())
                .contains(
                        "Tổng quan",
                        "Lỗi 1: Calendar chỉ dùng time slot đầu tiên",
                        "Vấn đề",
                        "Cách sửa",
                        "Prompt Gemini Calendar",
                        "Gemini request config",
                        "Tests đã thêm/cập nhật",
                        "Build / Test result",
                        "Kết luận"
                );
    }

    @Test
    void analyzeDetectsNoAccentTechnicalReportHeadings() {
        MaterialChunk chunk = chunk("""
                # Bao cao sua loi Calendar AI
                1. Tong quan
                2. Loi 1
                Van de
                Cach sua
                Build / Test result
                Ket luan
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.LECTURE_NOTE);
        assertThat(analysis.signals()).contains("technicalReportHeadings");
        assertThat(analysis.sections().stream().map(section -> section.title()).toList())
                .contains("Tong quan", "Loi 1", "Van de", "Cach sua", "Ket luan");
    }

    @Test
    void analyzeDetectsEnglishTechnicalReportHeadings() {
        MaterialChunk chunk = chunk("""
                ## Summary
                Calendar generation issue.
                ## Root Cause
                The planner used one selected time slot.
                ## Fix Implemented
                Parse every selected window.
                ## Verification
                Added regression tests.
                ## Final Result
                Calendar output respects availability.
                """);

        DocumentAnalysis analysis = LearningDocumentAnalyzer.analyze(List.of(chunk));

        assertThat(analysis.kind()).isEqualTo(DocumentKind.LECTURE_NOTE);
        assertThat(analysis.signals()).contains("technicalReportHeadings");
        assertThat(analysis.sections().stream().map(section -> section.title()).toList())
                .contains("Summary", "Root Cause", "Fix Implemented", "Verification", "Final Result");
    }

    private MaterialChunk chunk(String content) {
        MaterialChunk chunk = new MaterialChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setContent(content);
        return chunk;
    }
}
