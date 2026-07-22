package com.skillsprint.configuration.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

/** Logs Gemini operational metadata without recording prompts, model output, or credentials. */
public final class GeminiResponseMetrics {

    private GeminiResponseMetrics() {
    }

    public static void logCompletion(
            Logger log,
            ObjectMapper objectMapper,
            String workflow,
            String model,
            long startedAtNanos,
            String responseText
    ) {
        long latencyMs = (System.nanoTime() - startedAtNanos) / 1_000_000;

        if (responseText == null || responseText.isBlank()) {
            log.info("[AI] Gemini {} completed: model={}, latencyMs={}, usageMetadata=unavailable",
                    workflow, model, latencyMs);
            return;
        }

        try {
            JsonNode usage = objectMapper.readTree(responseText).path("usageMetadata");
            log.info(
                    "[AI] Gemini {} completed: model={}, latencyMs={}, inputTokens={}, outputTokens={}, thinkingTokens={}, totalTokens={}",
                    workflow,
                    model,
                    latencyMs,
                    usage.path("promptTokenCount").asInt(0),
                    usage.path("candidatesTokenCount").asInt(0),
                    usage.path("thoughtsTokenCount").asInt(0),
                    usage.path("totalTokenCount").asInt(0)
            );
        } catch (JsonProcessingException ex) {
            log.info("[AI] Gemini {} completed: model={}, latencyMs={}, usageMetadata=unavailable",
                    workflow, model, latencyMs);
        }
    }
}
