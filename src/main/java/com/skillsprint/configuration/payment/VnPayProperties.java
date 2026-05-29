package com.skillsprint.configuration.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment.vnpay")
public record VnPayProperties(
        boolean enabled,
        String payUrl,
        String returnUrl,
        String tmnCode,
        String hashSecret,
        int expireMinutes
) {

    public boolean ready() {
        return enabled
                && payUrl != null && !payUrl.isBlank()
                && returnUrl != null && !returnUrl.isBlank()
                && tmnCode != null && !tmnCode.isBlank()
                && hashSecret != null && !hashSecret.isBlank();
    }

    public int expireMinutesValue() {
        return expireMinutes <= 0 ? 15 : expireMinutes;
    }
}