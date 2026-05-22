package com.skillsprint.configuration.cognito;

import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CognitoSecretHashUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    public String calculateSecretHash(String username, String clientId, String clientSecret) {
        if (clientSecret == null || clientSecret.isBlank()) {
            return null;
        }

        try {
            String message = username + clientId;
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] rawHmac = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.COGNITO_SECRET_HASH_FAILED);
        }
    }
}
