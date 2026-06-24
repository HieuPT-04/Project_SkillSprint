package com.skillsprint.service.learningstructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentAnalysis;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.DocumentSection;
import com.skillsprint.service.learningstructure.LearningDocumentAnalyzer.SyllabusSlot;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GeminiLearningStructureClient {

    GeminiProperties properties;
    ObjectMapper objectMapper;
    RestClient.Builder restClientBuilder;

    public boolean isReady() {
        return properties.ready();
    }

    public AiLearningStructureDraft generate(List<MaterialChunk> chunks) {
        return generate(chunks, null);
    }

    public AiLearningStructureDraft generate(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        if (!isReady() || chunks == null || chunks.isEmpty()) {
            return null;
        }

        try {
            String responseText = restClientBuilder.clone()
                    .baseUrl(properties.baseUrl())
                    .build()
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta/models/{model}:generateContent")
                    .build(properties.model()))
                    .header("x-goog-api-key", properties.apiKey())
                    .body(buildRequestBody(chunks, analysis))
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseText);
        } catch (RestClientException | JsonProcessingException ex) {
            log.warn("[AI] Gemini learning structure generation failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequestBody(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return Map.of(
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(chunks, analysis))))),
                "generationConfig",
                Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );
    }

    String buildPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        if (analysis == null) {
            return buildGeneralPrompt(chunks, null);
        }

        return switch (analysis.kind()) {
            case SYLLABUS -> buildSyllabusPrompt(chunks, analysis);
            case LECTURE_NOTE -> buildLectureNotePrompt(chunks, analysis);
            case SLIDE_DECK -> buildSlideDeckPrompt(chunks, analysis);
            case ASSIGNMENT -> buildAssignmentPrompt(chunks, analysis);
            case GENERAL -> buildGeneralPrompt(chunks, analysis);
        };
    }

    private String buildGeneralPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                You are the SkillSprint learning-structure generator.

                Read the material chunks below and return a single valid JSON object only. No markdown, no explanation.

                Required schema:
                %s

                Rules:
                - Short documents get few chapters/topics; long documents get a reasonable number of chapters/topics.
                - Prefer to keep the document's existing heading structure when it has headings.
                - Do not invent content beyond the material.
                - Every chapter must have at least one topic.
                - Keep titles short and meaningful, at most 12 words.
                - Write all user-facing generated content in Vietnamese, including titles, summaries, summaryContent,
                  whatToLearn, keyConcepts, learningOutcomes, recommendedFocus, and warnings.
                - Do not include raw outline numbering in titles. Do not start a title with prefixes such as
                  "1.", "1.1.", "2 -", "3:", "Step 1", "Topic 1", or "Bước 1". Titles must be clean display titles only.
                - summary: 1-2 short sentences.
                - whatToLearn: 2-4 short items.
                - keyConcepts: 3-6 short concepts.
                - learningOutcomes: 2-4 short learning outcomes.
                - recommendedFocus: 2-3 short suggestions.
                - Keep each array item under 120 characters.
                - sourceChunkIds may only use ids present in the input.
                - warnings is always an array (empty if there are none).

                Backend pre-analysis:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildAnalysisText(analysis), buildChunkText(chunks));
    }

    private String buildSyllabusPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                You are the SkillSprint learning-structure generator.

                The input document is a SYLLABUS / course outline. Build a concise, clear learning structure that follows the session schedule.
                Return a single valid JSON object only. No markdown, no explanation.

                Required schema:
                %s

                Syllabus-specific rules:
                - Do not create chapters named "Syllabus details", "Course Description", "Assessment Scheme", or "Learning Materials".
                - Do not turn credits, prerequisites, grading, or attendance into main study chapters.
                - Prefer building chapters from the Session Schedule / Slot / Topic.
                - Group related slots into natural learning modules.
                - For 10 or more slots, create about 4-8 chapters.
                - For 5-9 slots, create about 3-5 chapters.
                - For fewer than 5 slots, create 2-3 chapters.
                - Each topic should map to one or a few real session slots.
                - Keep titles short and meaningful, at most 10 words.
                - Write all user-facing generated content in Vietnamese, including titles, summaries, summaryContent,
                  whatToLearn, keyConcepts, learningOutcomes, recommendedFocus, and warnings.
                - Do not include raw outline numbering in titles. Do not start a title with prefixes such as
                  "1.", "1.1.", "2 -", "3:", "Step 1", "Topic 1", or "Bước 1". Titles must be clean display titles only.
                - summary: 1 short sentence; do not copy the whole table.
                - whatToLearn: 2-4 short items.
                - keyConcepts: 3-6 short concepts.
                - learningOutcomes: 2-4 short learning outcomes.
                - recommendedFocus: 2-3 short suggestions.
                - Keep each array item under 120 characters.
                - sourceChunkIds may only use ids present in the input.
                - warnings is always an array (empty if there are none).

                Detected session schedule:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildSyllabusScheduleText(analysis.syllabusSlots()), buildChunkText(chunks));
    }

    private String buildLectureNotePrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                You are the SkillSprint learning-structure generator.

                The input document is a textbook / lecture note. Split it by the real headings and flow of knowledge in the document.
                Return a single valid JSON object only. No markdown, no explanation.

                Required schema:
                %s

                Lecture-note-specific rules:
                - Prefer top-level headings as chapters.
                - Use lower-level headings or sub-content as topics.
                - Do not collapse the whole document into one chapter when the backend detected multiple sections.
                - Do not copy long passages into titles/summaries.
                - Titles at most 10 words; summary 1-2 sentences.
                - Each chapter has 1-5 topics.
                - Write all user-facing generated content in Vietnamese, including titles, summaries, summaryContent,
                  whatToLearn, keyConcepts, learningOutcomes, recommendedFocus, and warnings.
                - Do not include raw outline numbering in titles. Do not start a title with prefixes such as
                  "1.", "1.1.", "2 -", "3:", "Step 1", "Topic 1", or "Bước 1". Titles must be clean display titles only.
                - sourceChunkIds may only use ids present in the input.

                Detected sections:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildSectionText(analysis.sections()), buildChunkText(chunks));
    }

    private String buildSlideDeckPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                You are the SkillSprint learning-structure generator.

                The input document is a slide deck. Slides are usually short and fragmented, so group nearby slides into learning modules.
                Return a single valid JSON object only. No markdown, no explanation.

                Required schema:
                %s

                Slide-deck-specific rules:
                - Do not turn every slide into a chapter when the content is too short.
                - Group 2-5 slides on the same topic into one chapter.
                - A topic should be a concrete learning/practice point, not just "Slide 1".
                - Keep titles short and clear, at most 10 words.
                - summary: 1 short sentence.
                - Write all user-facing generated content in Vietnamese, including titles, summaries, summaryContent,
                  whatToLearn, keyConcepts, learningOutcomes, recommendedFocus, and warnings.
                - Do not include raw outline numbering in titles. Do not start a title with prefixes such as
                  "1.", "1.1.", "2 -", "3:", "Step 1", "Topic 1", or "Bước 1". Titles must be clean display titles only.
                - sourceChunkIds may only use ids present in the input.

                Detected sections/slides:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildSectionText(analysis.sections()), buildChunkText(chunks));
    }

    private String buildAssignmentPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                You are the SkillSprint learning-structure generator.

                The input document is an assignment / exercise. Turn the assignment requirements into study and practice steps.
                Return a single valid JSON object only. No markdown, no explanation.

                Required schema:
                %s

                Assignment-specific rules:
                - A chapter should be a group of skills needed to complete the assignment.
                - A topic should be a concrete learning/practice activity.
                - Prioritize requirements, deliverables, rubric, deadline, and grading criteria.
                - Do not create a chapter named only "Assignment" or "Requirements".
                - For a small assignment, create only 2-4 chapters.
                - Keep titles short and clear, at most 10 words.
                - Write all user-facing generated content in Vietnamese, including titles, summaries, summaryContent,
                  whatToLearn, keyConcepts, learningOutcomes, recommendedFocus, and warnings.
                - Do not include raw outline numbering in titles. Do not start a title with prefixes such as
                  "1.", "1.1.", "2 -", "3:", "Step 1", "Topic 1", or "Bước 1". Titles must be clean display titles only.
                - sourceChunkIds may only use ids present in the input.

                Backend pre-analysis:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildAnalysisText(analysis), buildChunkText(chunks));
    }

    private String responseSchema() {
        return """
                {
                  "confidenceScore": 0.85,
                  "warnings": ["string"],
                  "chapters": [
                    {
                      "title": "string",
                      "summary": "string",
                      "whatToLearn": ["string"],
                      "keyConcepts": ["string"],
                      "learningOutcomes": ["string"],
                      "recommendedFocus": ["string"],
                      "difficulty": "EASY|MEDIUM|HARD",
                      "estimatedMinutes": 30,
                      "topics": [
                        {
                          "title": "string",
                          "summaryContent": "string",
                          "whatToLearn": ["string"],
                          "keyConcepts": ["string"],
                          "learningOutcomes": ["string"],
                          "recommendedFocus": ["string"],
                          "difficulty": "EASY|MEDIUM|HARD",
                          "estimatedMinutes": 15,
                          "sourceChunkIds": ["related chunk id"]
                        }
                      ]
                    }
                  ]
                }
                """;
    }

    private String buildAnalysisText(DocumentAnalysis analysis) {
        if (analysis == null) {
            return "documentKind=GENERAL";
        }
        return "documentKind=" + analysis.kind()
                + ", sections=" + analysis.sections().size()
                + ", syllabusSlots=" + analysis.syllabusSlots().size()
                + ", signals=" + analysis.signals();
    }

    private String buildSyllabusScheduleText(List<SyllabusSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return "No clear slot table detected. Find the Session Schedule inside the material chunks yourself.";
        }

        StringBuilder builder = new StringBuilder();
        for (SyllabusSlot slot : slots) {
            builder.append("- Slot ")
                    .append(slot.slot())
                    .append(": ")
                    .append(slot.topic());
            if (slot.details() != null && !slot.details().isEmpty()) {
                builder.append(" | ").append(String.join(" | ", slot.details()));
            }
            if (slot.sourceChunkIds() != null && !slot.sourceChunkIds().isEmpty()) {
                builder.append(" | sourceChunkIds=").append(slot.sourceChunkIds());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private String buildSectionText(List<DocumentSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return "No clear section detected.";
        }

        StringBuilder builder = new StringBuilder();
        for (DocumentSection section : sections.stream().limit(30).toList()) {
            builder.append("- level=")
                    .append(section.level())
                    .append(", title=")
                    .append(section.title())
                    .append(", sourceChunkIds=")
                    .append(section.sourceChunkIds())
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private String buildChunkText(List<MaterialChunk> chunks) {
        StringBuilder builder = new StringBuilder();
        int limit = properties.inputLimit();

        for (MaterialChunk chunk : chunks) {
            if (builder.length() >= limit) {
                break;
            }

            String content = chunk.getContent() == null ? "" : chunk.getContent().trim();
            if (content.isBlank()) {
                continue;
            }

            String block = """

                    [chunkId=%s, chunkIndex=%s]
                    %s
                    """.formatted(chunk.getChunkId(), chunk.getChunkIndex(), content);

            if (builder.length() + block.length() > limit) {
                builder.append(block, 0, Math.max(0, limit - builder.length()));
                break;
            }
            builder.append(block);
        }

        return builder.toString();
    }

    private AiLearningStructureDraft parseResponse(String responseText) throws JsonProcessingException {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(responseText);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null;
        }

        JsonNode textNode = candidates.path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text");

        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            return null;
        }

        String json = cleanJson(textNode.asText());
        if (json.isBlank()) {
            return null;
        }

        return objectMapper.readValue(json, AiLearningStructureDraft.class);
    }

    private String cleanJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String value = raw.trim();
        if (value.startsWith("```")) {
            value = value.replaceFirst("(?s)^```(?:json)?\\s*", "").trim();
            value = value.replaceFirst("(?s)\\s*```$", "").trim();
        }

        int firstBrace = value.indexOf('{');
        int lastBrace = value.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return value.substring(firstBrace, lastBrace + 1).trim();
        }

        return value;
    }
}
