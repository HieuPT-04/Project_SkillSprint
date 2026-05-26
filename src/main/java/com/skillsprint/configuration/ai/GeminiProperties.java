package com.skillsprint.configuration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai.gemini")
public record GeminiProperties(
        boolean enabled,
        String apiKey,
        String model,
        String baseUrl,
        int maxInputChars
) {

    public boolean ready() {
        return enabled
                && apiKey != null
                && !apiKey.isBlank()
                && model != null
                && !model.isBlank()
                && baseUrl != null
                && !baseUrl.isBlank();
    }

    public int inputLimit() {
        return maxInputChars <= 0 ? 18000 : maxInputChars;
    }
}
