package com.skillsprint.service.calendar.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillsprint.configuration.ai.GeminiProperties;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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

    // Per-task duration must be a human-friendly study block: a multiple of 15 minutes within
    // [MIN_DURATION_MINUTES, MAX_DURATION_MINUTES]. This mirrors StudySessionSizingPolicy so the AI
    // cannot reintroduce odd values like 80/96/113; anything else is rejected and the caller falls
    // back to the rule-based plan.
    private static final int DURATION_BLOCK_MINUTES = 15;
    private static final int MIN_DURATION_MINUTES = 30;
    private static final int MAX_DURATION_MINUTES = 120;
    // Downstream (CalendarService) cleans/truncates titles to 90 and descriptions
    // to 250 chars; these caps only reject obviously runaway AI output.
    private static final int MAX_TITLE_LENGTH = 160;
    private static final int MAX_DESCRIPTION_LENGTH = 1000;

    // Mechanical numbered prefixes we never want as a task title.
    private static final Pattern MECHANICAL_TITLE_PREFIX = Pattern.compile(
            "^\\s*(bước|buổi|bài|chương|phần|step|topic|task|part|day|ngày|module|lesson|unit)\\s*\\d",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_NUMBERING = Pattern.compile("^\\s*\\d+\\s*[.)\\-:]");

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

            return parseResponse(responseText, tasks);
        } catch (RestClientException | JsonProcessingException ex) {
            log.warn("[AI] Gemini calendar planning failed: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildRequestBody(List<AiCalendarTaskInput> tasks) throws JsonProcessingException {
        return Map.of(
                "contents",
                List.of(Map.of("parts", List.of(Map.of("text", buildPrompt(tasks))))),
                "generationConfig",
                Map.of(
                        "temperature", 0.1,
                        "candidateCount", 1,
                        "maxOutputTokens", 8192,
                        "responseMimeType", "application/json",
                        "responseSchema", buildResponseSchema(),
                        // gemini-2.5-flash is a thinking model; disable thinking so the
                        // output-token budget is not spent before the JSON is produced.
                        "thinkingConfig", Map.of("thinkingBudget", 0)
                )
        );
    }

    private Map<String, Object> buildResponseSchema() {
        Map<String, Object> taskProperties = Map.ofEntries(
                Map.entry("taskIndex", Map.of("type", "INTEGER")),
                Map.entry("title", Map.of("type", "STRING")),
                Map.entry("description", Map.of("type", "STRING")),
                Map.entry("taskDate", Map.of("type", "STRING")),
                Map.entry("startTime", Map.of("type", "STRING")),
                Map.entry("durationMinutes", Map.of("type", "INTEGER")),
                Map.entry("category", Map.of(
                        "type", "STRING",
                        "enum", List.of("DEEP_STUDY", "REVIEW", "PRACTICE", "PROJECT", "PERSONAL"))),
                Map.entry("priority", Map.of(
                        "type", "STRING",
                        "enum", List.of("LOW", "MEDIUM", "HIGH"))),
                Map.entry("reason", Map.of("type", "STRING")));

        Map<String, Object> taskSchema = Map.of(
                "type", "OBJECT",
                "properties", taskProperties,
                "required", List.of(
                        "taskIndex", "title", "description", "taskDate",
                        "startTime", "durationMinutes", "category", "priority", "reason"),
                "propertyOrdering", List.of(
                        "taskIndex", "title", "description", "taskDate",
                        "startTime", "durationMinutes", "category", "priority", "reason"));

        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "warnings", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                        "tasks", Map.of("type", "ARRAY", "items", taskSchema)),
                "required", List.of("warnings", "tasks"),
                "propertyOrdering", List.of("warnings", "tasks"));
    }

    private String buildPrompt(List<AiCalendarTaskInput> tasks) throws JsonProcessingException {
        return """
                You are the SkillSprint study-calendar planner.

                Return a single valid JSON object only. No markdown, no explanation.
                Rules:
                - Do not add or remove tasks. Keep the learning order: a lower taskIndex must be studied before a higher one.
                - Each taskIndex appears exactly once and matches the input.
                - Do not invent content beyond the task input.
                - Do not overlap times within the same day.
                - Keep every task within the study days and time windows reflected by its suggestedTaskDate
                  and suggestedStartTime. Treat suggestedTaskDate as a suggestion you may fine-tune, not a locked
                  date, but never move a task to a different day-of-week or outside its suggested time window.
                - suggestedDurationMinutes is a planned, human-friendly study block. Reuse it as-is whenever
                  possible. The selected time windows are availability pools, NOT a list of required sessions:
                  do not create one task per window and do not multiply the number of tasks.
                - durationMinutes must be a MULTIPLE OF 15, stay between 30 and 120, fit inside the task's
                  suggested time window, and must not push the task past that window. Never invent odd
                  durations such as 80, 96 or 113 minutes; prefer 45, 60, 75, 90 and only use 105 or 120 when needed.
                - title must be short and clear; do NOT start it with mechanical numbered prefixes such as
                  "Step 1", "Topic 1", "Task 1".
                - taskDate format YYYY-MM-DD, startTime format HH:mm:ss. category and priority must be valid enums.
                - warnings is always an array (empty if there are none).

                You may fine-tune date, time, duration, title, description, category, and priority.

                Task inputs:
                %s
                """.formatted(toJson(tasks));
    }

    private String toJson(List<AiCalendarTaskInput> tasks) throws JsonProcessingException {
        return objectMapper.writeValueAsString(tasks);
    }

    AiCalendarPlanDraft parseResponse(String responseText, List<AiCalendarTaskInput> tasks)
            throws JsonProcessingException {
        if (responseText == null || responseText.isBlank()) {
            return null;
        }

        JsonNode root = objectMapper.readTree(responseText);

        JsonNode promptFeedback = root.path("promptFeedback");
        String blockReason = promptFeedback.path("blockReason").asText(null);
        if (blockReason != null && !blockReason.isBlank()) {
            log.warn("[AI] Gemini calendar planning blocked by promptFeedback: {}", blockReason);
            return null;
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            log.warn("[AI] Gemini calendar planning returned no candidates");
            return null;
        }

        JsonNode candidate = candidates.path(0);
        String finishReason = candidate.path("finishReason").asText(null);
        if (finishReason != null && !finishReason.isBlank() && !"STOP".equals(finishReason)) {
            log.warn("[AI] Gemini calendar planning non-STOP finishReason: {}", finishReason);
            return null;
        }

        JsonNode textNode = candidate.path("content").path("parts").path(0).path("text");
        if (textNode.isMissingNode() || textNode.asText().isBlank()) {
            log.warn("[AI] Gemini calendar planning returned empty content");
            return null;
        }

        String json = cleanJson(textNode.asText());
        if (json.isBlank()) {
            return null;
        }

        // Truncated/malformed model output (e.g. cut off before MAX_TOKENS is reported) must
        // never escape as a partial draft. Reject it here; generate() also catches parse errors.
        AiCalendarPlanDraft draft;
        try {
            draft = objectMapper.readValue(json, AiCalendarPlanDraft.class);
        } catch (JsonProcessingException ex) {
            log.warn("[AI] Gemini calendar planning returned unparseable task JSON");
            return null;
        }
        return validateDraft(draft, tasks) ? draft : null;
    }

    private boolean validateDraft(AiCalendarPlanDraft draft, List<AiCalendarTaskInput> inputs) {
        if (draft == null || draft.tasks() == null) {
            log.warn("[AI] Invalid calendar draft: null draft or tasks");
            return false;
        }
        if (draft.tasks().size() != inputs.size()) {
            log.warn("[AI] Invalid calendar draft: task count {} != input count {}",
                    draft.tasks().size(), inputs.size());
            return false;
        }

        int size = inputs.size();
        AiCalendarTaskSuggestion[] byIndex = new AiCalendarTaskSuggestion[size];

        for (AiCalendarTaskSuggestion suggestion : draft.tasks()) {
            if (suggestion == null || suggestion.taskIndex() == null) {
                log.warn("[AI] Invalid calendar draft: null task or taskIndex");
                return false;
            }
            int index = suggestion.taskIndex();
            if (index < 0 || index >= size) {
                log.warn("[AI] Invalid calendar draft: taskIndex {} out of range", index);
                return false;
            }
            if (byIndex[index] != null) {
                log.warn("[AI] Invalid calendar draft: duplicate taskIndex {}", index);
                return false;
            }
            byIndex[index] = suggestion;

            if (!isValidTask(suggestion)) {
                return false;
            }
        }

        // Count + range + no-duplicate guarantees every index is present, but keep
        // an explicit check so a future change cannot leave a hole unnoticed.
        for (int i = 0; i < size; i++) {
            if (byIndex[i] == null) {
                log.warn("[AI] Invalid calendar draft: missing taskIndex {}", i);
                return false;
            }
        }

        return hasMonotonicSchedule(byIndex);
    }

    private boolean isValidTask(AiCalendarTaskSuggestion task) {
        if (task.title() == null || task.title().isBlank()) {
            log.warn("[AI] Invalid calendar draft: blank title at index {}", task.taskIndex());
            return false;
        }
        if (task.title().length() > MAX_TITLE_LENGTH) {
            log.warn("[AI] Invalid calendar draft: title too long at index {}", task.taskIndex());
            return false;
        }
        if (MECHANICAL_TITLE_PREFIX.matcher(task.title()).find()
                || LEADING_NUMBERING.matcher(task.title()).find()) {
            log.warn("[AI] Invalid calendar draft: mechanical title prefix at index {}", task.taskIndex());
            return false;
        }
        if (task.description() == null || task.description().length() > MAX_DESCRIPTION_LENGTH) {
            log.warn("[AI] Invalid calendar draft: null/oversized description at index {}", task.taskIndex());
            return false;
        }
        // taskDate / startTime / category / priority are typed in the record, so an
        // unparseable value or invalid enum already fails Jackson deserialization.
        if (task.taskDate() == null || task.startTime() == null) {
            log.warn("[AI] Invalid calendar draft: missing date/time at index {}", task.taskIndex());
            return false;
        }
        if (task.durationMinutes() == null
                || task.durationMinutes() < MIN_DURATION_MINUTES
                || task.durationMinutes() > MAX_DURATION_MINUTES
                || task.durationMinutes() % DURATION_BLOCK_MINUTES != 0) {
            log.warn("[AI] Invalid calendar draft: non-human-friendly duration at index {}", task.taskIndex());
            return false;
        }
        if (task.category() == null || task.priority() == null) {
            log.warn("[AI] Invalid calendar draft: missing category/priority at index {}", task.taskIndex());
            return false;
        }
        return true;
    }

    /**
     * Enforces strict learning order: each task (by ascending taskIndex) must start
     * at or after the previous task ends. This also guarantees no two tasks overlap.
     */
    private boolean hasMonotonicSchedule(AiCalendarTaskSuggestion[] byIndex) {
        for (int i = 1; i < byIndex.length; i++) {
            AiCalendarTaskSuggestion previous = byIndex[i - 1];
            AiCalendarTaskSuggestion current = byIndex[i];
            boolean beforePrevious = current.taskDate().isBefore(previous.taskDate())
                    || (current.taskDate().equals(previous.taskDate())
                    && current.startTime().isBefore(previous.startTime().plusMinutes(previous.durationMinutes())));
            if (beforePrevious) {
                log.warn("[AI] Invalid calendar draft: overlapping or out-of-order schedule at index {}", i);
                return false;
            }
        }
        return true;
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
