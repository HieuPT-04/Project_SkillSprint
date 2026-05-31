package com.skillsprint.configuration.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.sepay")
public record SepayProperties(
        boolean enabled,
        String bankCode,
        String bankAccountNumber,
        String bankAccountName,
        String webhookApiKey,
        String qrBaseUrl,
        int expireMinutes
) {

    public boolean ready() {
        return enabled
                && bankCode != null && !bankCode.isBlank()
                && bankAccountNumber != null && !bankAccountNumber.isBlank()
                && bankAccountName != null && !bankAccountName.isBlank()
                && qrBaseUrl != null && !qrBaseUrl.isBlank();
    }

    public int expireMinutesValue() {
        return expireMinutes <= 0 ? 15 : expireMinutes;
    }

    public boolean hasWebhookApiKey() {
        return webhookApiKey != null && !webhookApiKey.isBlank();
    }
}
