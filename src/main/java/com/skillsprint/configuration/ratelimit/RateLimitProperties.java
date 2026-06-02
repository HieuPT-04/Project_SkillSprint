package com.skillsprint.configuration.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        Rule login,
        Rule register,
        Rule resendConfirmationCode,
        Rule forgotPassword,
        Rule paymentCreate
) {

    private static final Rule DEFAULT_RULE = new Rule(5, 300);

    public Rule loginRule() {
        return validOrDefault(login);
    }

    public Rule registerRule() {
        return validOrDefault(register);
    }

    public Rule resendConfirmationCodeRule() {
        return validOrDefault(resendConfirmationCode);
    }

    public Rule forgotPasswordRule() {
        return validOrDefault(forgotPassword);
    }

    public Rule paymentCreateRule() {
        return validOrDefault(paymentCreate);
    }

    private Rule validOrDefault(Rule rule) {
        if (rule == null || rule.maxAttempts() <= 0 || rule.windowSeconds() <= 0) {
            return DEFAULT_RULE;
        }
        return rule;
    }

    public record Rule(
            int maxAttempts,
            long windowSeconds
    ) {
    }
}
