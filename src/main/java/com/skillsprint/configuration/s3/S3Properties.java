package com.skillsprint.configuration.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.s3")
public record S3Properties(
        String region,
        String bucket,
        String publicBaseUrl,
        String accessKeyId,
        String secretAccessKey,
        long uploadUrlExpirationMinutes
) {

    public boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }
}
