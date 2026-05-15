package com.skillsprint.configuration.cognito;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cognito")
public record CognitoProperties(
        String region,
        String userPoolId,
        String clientId,
        String clientSecret,
        String defaultGroup,
        String accessKeyId,
        String secretAccessKey
) {

    public boolean hasClientSecret() {
        return clientSecret != null && !clientSecret.isBlank();
    }

    public boolean hasStaticCredentials() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && secretAccessKey != null && !secretAccessKey.isBlank();
    }
}
