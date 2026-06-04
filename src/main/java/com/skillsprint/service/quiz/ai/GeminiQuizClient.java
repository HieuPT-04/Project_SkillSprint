package com.skillsprint.service.quiz.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
import com.skillsprint.entity.MaterialChunk;
import com.skillsprint.entity.RoadmapStep;
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
public class GeminiQuizClient {

    private static final int MAX_CHUNKS = 8;

    GeminiProperties properties;
    ObjectMapper objectMapper;
    RestClient.Builder restClientBuilder;

    public boolean isReady() {
        return properties.ready();
    }

    public AiQuizDraft generate(RoadmapStep step, List<MaterialChunk> chunks) {
        if (!isReady() || step == null || chunks == null || chunks.isEmpty()) {
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
                    .body(buildRequestBody(step, chunks))
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseText);
        } catch (RestClientException | JsonProcessingException ex) {
            log.warn("[AI] Gemini quiz generation failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequestBody(RoadmapStep step, List<MaterialChunk> chunks) {
        return Map.of(
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(step, chunks))))),
                "generationConfig",
                Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );
    }

    private String buildPrompt(RoadmapStep step, List<MaterialChunk> chunks) {
        return """
                Bạn là AI tạo quiz kiểm tra nhanh cho SkillSprint.

                Hãy tạo đúng 5 câu quiz dựa trên roadmap step và tài liệu bên dưới.
                Trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.

                Schema bắt buộc:
                {
                  "questions": [
                    {
                      "type": "SINGLE_CHOICE|TRUE_FALSE",
                      "question": "string",
                      "options": [
                        {"label": "A", "text": "string"}
                      ],
                      "correctLabel": "A",
                      "explanation": "string"
                    }
                  ]
                }

                Quy tắc:
                - Luôn tạo đúng 5 câu.
                - Ưu tiên SINGLE_CHOICE, có thể dùng TRUE_FALSE nếu phù hợp.
                - SINGLE_CHOICE phải có 4 đáp án A, B, C, D.
                - TRUE_FALSE phải có 2 đáp án: A = Đúng, B = Sai.
                - correctLabel phải khớp một label trong options.
                - Câu hỏi ngắn, rõ, không bắt đầu bằng "Bước", "Step", hoặc đánh số máy móc.
                - Không hỏi kiến thức ngoài tài liệu.
                - explanation ngắn, dễ hiểu, tối đa 240 ký tự.

                Roadmap step:
                title: %s
                subtitle: %s
                summary: %s
                keyConcepts: %s
                learningOutcomes: %s

                Material chunks:
                %s
                """.formatted(
                safe(step.getTitle()),
                safe(step.getSubtitle()),
                safe(step.getSummary()),
                toJson(step.getKeyConcepts()),
                toJson(step.getLearningOutcomes()),
                buildChunkContext(chunks)
        );
    }

    private String buildChunkContext(List<MaterialChunk> chunks) {
        int limit = Math.max(1000, properties.inputLimit());
        StringBuilder builder = new StringBuilder();
        for (MaterialChunk chunk : chunks.stream().limit(MAX_CHUNKS).toList()) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                continue;
            }
            builder.append("\n[")
                    .append(chunk.getSectionTitle() == null ? "section" : chunk.getSectionTitle())
                    .append("]\n")
                    .append(chunk.getContent())
                    .append("\n");
            if (builder.length() >= limit) {
                break;
            }
        }
        String context = builder.toString();
        return context.length() > limit ? context.substring(0, limit) : context;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private AiQuizDraft parseResponse(String responseText) throws JsonProcessingException {
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

        return objectMapper.readValue(json, AiQuizDraft.class);
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

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
