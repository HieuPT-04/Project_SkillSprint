package com.skillsprint.service.auth;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSessionService {

    private static final String USER_SESSION_KEY_PREFIX = "user_session:";
    private static final String SESSION_USER_KEY_PREFIX = "session_user:";

    StringRedisTemplate redisTemplate;

    @Value("${app.session.enabled:true}")
    @NonFinal
    boolean sessionEnabled;

    public String createSession(String userId, Integer expiresInSeconds) {
        if (!sessionEnabled) {
            return null;
        }

        String sessionId = UUID.randomUUID().toString();
        Duration ttl = Duration.ofSeconds(resolveTtlSeconds(expiresInSeconds));
        String userSessionKey = userSessionKey(userId);
        String sessionUserKey = sessionUserKey(sessionId);

        String oldSessionId = redisTemplate.opsForValue().get(userSessionKey);
        if (oldSessionId != null && !oldSessionId.isBlank()) {
            redisTemplate.delete(sessionUserKey(oldSessionId));
        }

        redisTemplate.opsForValue().set(userSessionKey, sessionId, ttl);
        redisTemplate.opsForValue().set(sessionUserKey, userId, ttl);
        return sessionId;
    }

    public boolean isCurrentSession(String userId, String sessionId) {
        if (!sessionEnabled) {
            return true;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        String currentSessionId = redisTemplate.opsForValue().get(userSessionKey(userId));
        return sessionId.equals(currentSessionId);
    }

    public void invalidateSession(String userId, String sessionId) {
        if (!sessionEnabled || sessionId == null || sessionId.isBlank()) {
            return;
        }

        String currentSessionId = redisTemplate.opsForValue().get(userSessionKey(userId));
        if (sessionId.equals(currentSessionId)) {
            redisTemplate.delete(userSessionKey(userId));
        }
        redisTemplate.delete(sessionUserKey(sessionId));
    }

    public void invalidateSession(String sessionId) {
        if (!sessionEnabled || sessionId == null || sessionId.isBlank()) {
            return;
        }

        Optional.ofNullable(redisTemplate.opsForValue().get(sessionUserKey(sessionId)))
                .ifPresent(userId -> invalidateSession(userId, sessionId));
    }

    private long resolveTtlSeconds(Integer expiresInSeconds) {
        if (expiresInSeconds == null || expiresInSeconds <= 0) {
            return Duration.ofHours(1).toSeconds();
        }
        return expiresInSeconds;
    }

    private String userSessionKey(String userId) {
        return USER_SESSION_KEY_PREFIX + userId;
    }

    private String sessionUserKey(String sessionId) {
        return SESSION_USER_KEY_PREFIX + sessionId;
    }
}
