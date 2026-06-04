package com.skillsprint.service.learningstructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentAnalysis;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentKind;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LearningDocumentAnalyzerTest {

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

    private MaterialChunk chunk(String content) {
        MaterialChunk chunk = new MaterialChunk();
        chunk.setChunkId(UUID.randomUUID());
        chunk.setContent(content);
        return chunk;
    }
}
