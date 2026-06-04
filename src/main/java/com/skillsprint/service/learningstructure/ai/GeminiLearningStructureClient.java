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

    private String buildPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
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
                Bạn là AI tạo cấu trúc học tập cho SkillSprint.

                Hãy đọc các material chunks bên dưới và trả về JSON hợp lệ, không markdown, không giải thích thêm.

                Schema bắt buộc:
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
                          "sourceChunkIds": ["chunk id liên quan"]
                        }
                      ]
                    }
                  ]
                }

                Quy tắc:
                - Tài liệu ngắn thì tạo ít chapter/topic.
                - Tài liệu dài thì tạo nhiều chapter/topic vừa đủ.
                - Ưu tiên giữ cấu trúc heading nếu tài liệu có heading.
                - Không bịa nội dung ngoài tài liệu.
                - Mỗi chapter nên có ít nhất 1 topic.
                - Title ngắn, rõ ý, tối đa 12 từ.
                - Summary chỉ 1-2 câu ngắn.
                - whatToLearn trả 2-4 ý ngắn.
                - keyConcepts trả 3-6 khái niệm ngắn.
                - learningOutcomes trả 2-4 kết quả học tập ngắn.
                - recommendedFocus trả 2-3 gợi ý ngắn.
                - Mỗi item trong array nên dưới 120 ký tự.
                - sourceChunkIds chỉ dùng id có trong input.
                - warnings là array, có thể rỗng.

                Phân tích sơ bộ của backend:
                %s

                Material chunks:
                %s
                """.formatted(buildAnalysisText(analysis), buildChunkText(chunks));
    }

    private String buildSyllabusPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                Bạn là AI tạo cấu trúc học tập cho SkillSprint.

                Tài liệu đầu vào là SYLLABUS / đề cương môn học. Hãy tạo cấu trúc học tập gọn, rõ và bám theo lịch học.
                Trả về JSON hợp lệ, không markdown, không giải thích thêm.

                Schema bắt buộc:
                %s

                Quy tắc riêng cho syllabus:
                - Không tạo chapter tên "Syllabus details", "Course Description", "Assessment Scheme", "Learning Materials".
                - Không biến credits, prerequisite, grading, attendance thành chương học chính.
                - Ưu tiên tạo chapter từ Session Schedule / Slot / Topic.
                - Gom các slot liên quan thành module học tự nhiên.
                - Nếu có từ 10 slot trở lên, tạo khoảng 4-8 chapter.
                - Nếu có 5-9 slot, tạo khoảng 3-5 chapter.
                - Nếu dưới 5 slot, tạo 2-3 chapter.
                - Mỗi topic nên bám theo 1 hoặc vài slot học thật.
                - Title ngắn, rõ ý, tối đa 10 từ.
                - Summary chỉ 1 câu ngắn, không copy nguyên bảng.
                - whatToLearn trả 2-4 ý ngắn.
                - keyConcepts trả 3-6 khái niệm ngắn.
                - learningOutcomes trả 2-4 kết quả học tập ngắn.
                - recommendedFocus trả 2-3 gợi ý ngắn.
                - Mỗi item trong array dưới 120 ký tự.
                - sourceChunkIds chỉ dùng id có trong input.
                - warnings là array, có thể rỗng.

                Session schedule đã detect:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildSyllabusScheduleText(analysis.syllabusSlots()), buildChunkText(chunks));
    }

    private String buildLectureNotePrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                Bạn là AI tạo cấu trúc học tập cho SkillSprint.

                Tài liệu đầu vào là giáo trình / lecture note. Hãy chia theo heading và mạch kiến thức thật trong tài liệu.
                Trả về JSON hợp lệ, không markdown, không giải thích thêm.

                Schema bắt buộc:
                %s

                Quy tắc riêng cho lecture note:
                - Ưu tiên heading cấp lớn làm chapter.
                - Heading cấp nhỏ hoặc nội dung con làm topic.
                - Không dồn toàn bộ tài liệu vào 1 chapter nếu backend detect nhiều section.
                - Không copy nguyên đoạn dài vào title/summary.
                - Title tối đa 10 từ, summary 1-2 câu.
                - Mỗi chapter có 1-5 topic.
                - sourceChunkIds chỉ dùng id có trong input.

                Sections đã detect:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildSectionText(analysis.sections()), buildChunkText(chunks));
    }

    private String buildSlideDeckPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                Bạn là AI tạo cấu trúc học tập cho SkillSprint.

                Tài liệu đầu vào là slide deck. Slide thường ngắn và rời rạc, hãy gom các slide gần nhau thành module học.
                Trả về JSON hợp lệ, không markdown, không giải thích thêm.

                Schema bắt buộc:
                %s

                Quy tắc riêng cho slide deck:
                - Không tạo mỗi slide thành một chapter nếu nội dung quá ngắn.
                - Gom 2-5 slide cùng chủ đề thành 1 chapter.
                - Topic nên là ý học/thực hành cụ thể, không chỉ là "Slide 1".
                - Title ngắn, rõ, tối đa 10 từ.
                - Summary 1 câu ngắn.
                - sourceChunkIds chỉ dùng id có trong input.

                Sections/slide đã detect:
                %s

                Material chunks:
                %s
                """.formatted(responseSchema(), buildSectionText(analysis.sections()), buildChunkText(chunks));
    }

    private String buildAssignmentPrompt(List<MaterialChunk> chunks, DocumentAnalysis analysis) {
        return """
                Bạn là AI tạo cấu trúc học tập cho SkillSprint.

                Tài liệu đầu vào là assignment / bài tập. Hãy biến yêu cầu bài tập thành các bước học và thực hành.
                Trả về JSON hợp lệ, không markdown, không giải thích thêm.

                Schema bắt buộc:
                %s

                Quy tắc riêng cho assignment:
                - Chapter nên là nhóm kỹ năng cần học để làm bài.
                - Topic nên là việc học/thực hành cụ thể.
                - Ưu tiên yêu cầu, deliverables, rubric, deadline, tiêu chí chấm.
                - Không tạo chapter chỉ tên "Assignment" hoặc "Requirements".
                - Nếu bài tập nhỏ, chỉ tạo 2-4 chapter.
                - Title ngắn, rõ, tối đa 10 từ.
                - sourceChunkIds chỉ dùng id có trong input.

                Phân tích sơ bộ:
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
                          "sourceChunkIds": ["chunk id liên quan"]
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
            return "Không detect được bảng slot rõ ràng. Hãy tự tìm Session Schedule trong material chunks.";
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
            return "Không detect được section rõ ràng.";
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
