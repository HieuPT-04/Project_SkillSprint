package com.skillsprint.service.tutor.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
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
public class GeminiTutorClient {

    GeminiProperties properties;
    ObjectMapper objectMapper;
    RestClient.Builder restClientBuilder;

    public boolean isReady() {
        return properties.ready();
    }

    public AiTutorDraft ask(String question, String context) {
        if (!isReady() || question == null || question.isBlank() || context == null || context.isBlank()) {
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
                    .body(buildRequestBody(question, context))
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseText);
        } catch (RestClientException | JsonProcessingException ex) {
            log.warn("[AI] Gemini tutor answer failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequestBody(String question, String context) {
        return Map.of(
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(question, context))))),
                "generationConfig",
                Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );
    }

    private String buildPrompt(String question, String context) {
        return """
                Bạn là AI Tutor của SkillSprint.

                Hãy trả lời câu hỏi của người học dựa trên context bài học bên dưới.
                Trả về JSON hợp lệ, không markdown, không giải thích ngoài JSON.

                Schema bắt buộc:
                {
                  "answer": "string",
                  "suggestedQuestions": ["string", "string", "string"],
                  "confidence": "HIGH|MEDIUM|LOW"
                }

                Quy tắc:
                - Trả lời bằng tiếng Việt.
                - Trả lời thẳng vào câu hỏi, không mở đầu vòng vo như "Trong ngữ cảnh này".
                - Answer chỉ 2-4 câu ngắn, dễ hiểu, ưu tiên ví dụ đơn giản nếu thật sự cần.
                - Không dùng markdown, không bullet, không đánh số trong answer trừ khi người học yêu cầu liệt kê.
                - Chỉ dùng thông tin trong context. Không bịa nội dung ngoài tài liệu.
                - Nếu context không đủ để trả lời, nói ngắn gọn rằng tài liệu hiện tại chưa đủ thông tin và gợi ý hỏi cụ thể hơn.
                - answer tối đa 450 ký tự.
                - suggestedQuestions luôn có đúng 3 câu, mỗi câu tối đa 80 ký tự.
                - Nếu confidence LOW, suggestedQuestions phải là câu hỏi học tập hữu ích, không nhắc lại máy móc tên workspace.
                - confidence là HIGH nếu context trả lời trực tiếp, MEDIUM nếu chỉ trả lời được một phần, LOW nếu context thiếu.

                Câu hỏi:
                %s

                Context:
                %s
                """.formatted(question, context);
    }

    private AiTutorDraft parseResponse(String responseText) throws JsonProcessingException {
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

        return objectMapper.readValue(json, AiTutorDraft.class);
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
