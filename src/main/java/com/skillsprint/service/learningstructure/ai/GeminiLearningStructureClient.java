package com.skillsprint.service.learningstructure.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
import com.skillsprint.entity.MaterialChunk;
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
                    .body(buildRequestBody(chunks))
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseText);
        } catch (RestClientException | JsonProcessingException ex) {
            log.warn("[AI] Gemini learning structure generation failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequestBody(List<MaterialChunk> chunks) {
        return Map.of(
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(chunks))))),
                "generationConfig",
                Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );
    }

    private String buildPrompt(List<MaterialChunk> chunks) {
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
                - sourceChunkIds chỉ dùng id có trong input.
                - warnings là array, có thể rỗng.

                Material chunks:
                %s
                """.formatted(buildChunkText(chunks));
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
