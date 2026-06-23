package com.skillsprint.configuration.cognito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CognitoSecretHashUtilTest {

    @Test
    void calculateSecretHashReturnsKnownHmacSha256Value() {
        String hash = CognitoSecretHashUtil.calculateSecretHash(
                "learner@example.com",
                "client-id",
                "client-secret"
        );

        assertEquals("7Qh2y2AtWm6HHbLyJSGjdDCTQoKqP9uPmlMkWxIWE18=", hash);
    }

    @Test
    void calculateSecretHashReturnsNullWhenClientSecretIsMissing() {
        assertNull(CognitoSecretHashUtil.calculateSecretHash("user", "client", null));
        assertNull(CognitoSecretHashUtil.calculateSecretHash("user", "client", "   "));
    }
}
