package com.skillsprint.configuration.cognito;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CognitoProperties.class)
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CognitoConfig {

    CognitoProperties properties;

    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        var builder = CognitoIdentityProviderClient.builder()
                .region(Region.of(properties.region()));

        if (properties.hasStaticCredentials()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    properties.accessKeyId(),
                                    properties.secretAccessKey()
                            )
                    )
            );
        }

        return builder.build();
    }
}
