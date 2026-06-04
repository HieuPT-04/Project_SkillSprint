package com.skillsprint.service.calendar.ai;

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
public class GeminiCalendarPlannerClient {

    GeminiProperties properties;
    ObjectMapper objectMapper;
    RestClient.Builder restClientBuilder;

    public boolean isReady() {
        return properties.ready();
    }

    public AiCalendarPlanDraft generate(List<AiCalendarTaskInput> tasks) {
        if (!isReady() || tasks == null || tasks.isEmpty()) {
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
                    .body(buildRequestBody(tasks))
                    .retrieve()
                    .body(String.class);

            return parseResponse(responseText);
        } catch (RestClientException | JsonProcessingException ex) {
            log.warn("[AI] Gemini calendar planning failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequestBody(List<AiCalendarTaskInput> tasks) {
        return Map.of(
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(tasks))))),
                "generationConfig",
                Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );
    }

    private String buildPrompt(List<AiCalendarTaskInput> tasks) {
        return """
                Bạn là AI planner tạo lịch học cá nhân cho SkillSprint.

                Hãy đọc danh sách task input và trả về JSON hợp lệ, không markdown, không giải thích thêm.
                Không thay đổi số lượng task và không đảo thứ tự học. Có thể tinh chỉnh ngày học, giờ học, thời lượng, title, description, category, priority.

                Schema bắt buộc:
                {
                  "warnings": ["string"],
                  "tasks": [
                    {
                      "taskIndex": 0,
                      "title": "string",
                      "description": "string",
                      "taskDate": "YYYY-MM-DD",
                      "startTime": "HH:mm:ss",
                      "durationMinutes": 60,
                      "category": "DEEP_STUDY|REVIEW|PRACTICE|PROJECT|PERSONAL",
                      "priority": "LOW|MEDIUM|HIGH",
                      "reason": "string"
                    }
                  ]
                }

                Quy tắc:
                - taskIndex phải đúng với input.
                - Giữ thứ tự học: taskIndex nhỏ hơn phải học trước hoặc cùng ngày trước giờ taskIndex lớn hơn.
                - Ưu tiên đặt task HARD vào buổi dài hơn, task REVIEW sau nhóm task cùng chapter.
                - Deadline gấp thì có thể tăng mật độ trong các ngày đã có suggestedTaskDate.
                - Không xếp task vào ngày không có trong suggestedTaskDate của input nếu không cần thiết.
                - Không tạo lịch trùng giờ trong cùng ngày.
                - title ngắn, rõ, tối đa 12 từ.
                - Không đặt title bắt đầu bằng "Bước 1", "Step 1", "Topic 1" hoặc các tiền tố đánh số máy móc tương tự.
                - description ngắn gọn, tối đa 220 ký tự.
                - durationMinutes nên là 30-120 phút, không vượt quá suggestedDurationMinutes quá nhiều.
                - category và priority chỉ dùng enum hợp lệ.
                - Không tạo nội dung ngoài ngữ cảnh task.
                - warnings luôn là array.

                Task inputs:
                %s
                """.formatted(toJson(tasks));
    }

    private String toJson(List<AiCalendarTaskInput> tasks) {
        try {
            return objectMapper.writeValueAsString(tasks);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private AiCalendarPlanDraft parseResponse(String responseText) throws JsonProcessingException {
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

        return objectMapper.readValue(json, AiCalendarPlanDraft.class);
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
