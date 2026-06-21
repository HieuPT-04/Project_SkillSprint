package com.skillsprint.service.ratelimit;

import com.skillsprint.configuration.ratelimit.RateLimitProperties;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RateLimitService {

    private static final String KEY_PREFIX = "rate_limit:";
    private static final String UNKNOWN_IDENTITY = "unknown";

    StringRedisTemplate redisTemplate;
    RateLimitProperties properties;

    public void checkLogin(String email, HttpServletRequest request) {
        checkByEmailAndIp("auth:login", email, request, properties.loginRule());
    }

    public void checkRegister(String email, HttpServletRequest request) {
        checkByEmailAndIp("auth:register", email, request, properties.registerRule());
    }

    public void checkResendConfirmationCode(String email, HttpServletRequest request) {
        checkByEmailAndIp("auth:resend-confirmation-code", email, request, properties.resendConfirmationCodeRule());
    }

    public void checkForgotPassword(String email, HttpServletRequest request) {
        checkByEmailAndIp("auth:forgot-password", email, request, properties.forgotPasswordRule());
    }

    public void checkPaymentCreate(String userId) {
        check("payment:create:user", userId, properties.paymentCreateRule());
    }

    public void checkCommunityChat(String userId, String roomId) {
        check("community:chat", normalize(userId) + ":" + normalize(roomId), properties.communityChatRule());
    }

    private void checkByEmailAndIp(
            String action,
            String email,
            HttpServletRequest request,
            RateLimitProperties.Rule rule
    ) {
        check(action + ":email", normalize(email), rule);
        check(action + ":ip", clientIp(request), rule);
    }

    private void check(String action, String identity, RateLimitProperties.Rule rule) {
        if (!properties.enabled()) {
            return;
        }

        try {
            String key = key(action, normalize(identity));
            Long count = redisTemplate.opsForValue().increment(key);

            if (count != null && count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(rule.windowSeconds()));
            }

            if (count != null && count > rule.maxAttempts()) {
                throw new AppException(ErrorCode.RATE_LIMIT_EXCEEDED);
            }
        } catch (AppException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("[RATE_LIMIT] Redis check failed for action {}: {}", action, exception.getMessage());
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN_IDENTITY;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return normalize(request.getRemoteAddr());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN_IDENTITY;
        }
        return value.trim().toLowerCase();
    }

    private String key(String action, String identity) {
        return KEY_PREFIX + action + ":" + sha256(identity);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
