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

    @Value("${app.session.ttl-days:30}")
    @NonFinal
    long sessionTtlDays;

    public String createSession(String userId) {
        if (!sessionEnabled) {
            return null;
        }

        String sessionId = UUID.randomUUID().toString();
        Duration ttl = sessionTtl();
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

    public void refreshSession(String userId, String sessionId) {
        if (!sessionEnabled) {
            return;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Duration ttl = sessionTtl();
        redisTemplate.opsForValue().set(userSessionKey(userId), sessionId, ttl);
        redisTemplate.opsForValue().set(sessionUserKey(sessionId), userId, ttl);
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

    private Duration sessionTtl() {
        if (sessionTtlDays <= 0) {
            return Duration.ofDays(30);
        }
        return Duration.ofDays(sessionTtlDays);
    }

    private String userSessionKey(String userId) {
        return USER_SESSION_KEY_PREFIX + userId;
    }

    private String sessionUserKey(String sessionId) {
        return SESSION_USER_KEY_PREFIX + sessionId;
    }
}
