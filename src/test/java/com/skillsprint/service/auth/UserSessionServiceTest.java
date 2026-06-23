package com.skillsprint.service.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserSessionServiceTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    UserSessionService sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new UserSessionService(redisTemplate);
        ReflectionTestUtils.setField(sessionService, "sessionEnabled", true);
        ReflectionTestUtils.setField(sessionService, "sessionTtlDays", 30L);
    }

    @Test
    void createSessionReturnsNullWithoutUsingRedisWhenDisabled() {
        ReflectionTestUtils.setField(sessionService, "sessionEnabled", false);

        assertNull(sessionService.createSession("user-1"));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void createSessionStoresBothMappingsWithConfiguredTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user_session:user-1")).thenReturn(null);

        String sessionId = sessionService.createSession("user-1");

        assertTrue(sessionId != null && !sessionId.isBlank());
        verify(valueOperations).set("user_session:user-1", sessionId, Duration.ofDays(30));
        verify(valueOperations).set("session_user:" + sessionId, "user-1", Duration.ofDays(30));
    }

    @Test
    void createSessionDeletesPreviousReverseMapping() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user_session:user-1")).thenReturn("old-session");

        String sessionId = sessionService.createSession("user-1");

        verify(redisTemplate).delete("session_user:old-session");
        verify(valueOperations).set("user_session:user-1", sessionId, Duration.ofDays(30));
    }

    @Test
    void createSessionFallsBackToThirtyDaysWhenConfiguredTtlIsInvalid() {
        ReflectionTestUtils.setField(sessionService, "sessionTtlDays", 0L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String sessionId = sessionService.createSession("user-1");

        verify(valueOperations).set("user_session:user-1", sessionId, Duration.ofDays(30));
    }

    @Test
    void refreshSessionIgnoresBlankSessionAndStoresValidSession() {
        sessionService.refreshSession("user-1", " ");
        verify(redisTemplate, never()).opsForValue();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        sessionService.refreshSession("user-1", "session-1");

        verify(valueOperations).set("user_session:user-1", "session-1", Duration.ofDays(30));
        verify(valueOperations).set("session_user:session-1", "user-1", Duration.ofDays(30));
    }

    @Test
    void isCurrentSessionHandlesDisabledBlankMatchingAndMismatchingSessions() {
        ReflectionTestUtils.setField(sessionService, "sessionEnabled", false);
        assertTrue(sessionService.isCurrentSession("user-1", null));

        ReflectionTestUtils.setField(sessionService, "sessionEnabled", true);
        assertFalse(sessionService.isCurrentSession("user-1", " "));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user_session:user-1")).thenReturn("session-1");
        assertTrue(sessionService.isCurrentSession("user-1", "session-1"));
        assertFalse(sessionService.isCurrentSession("user-1", "session-2"));
    }

    @Test
    void invalidateSessionDeletesUserMappingOnlyWhenItIsCurrent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user_session:user-1"))
                .thenReturn("session-1")
                .thenReturn("other-session");

        sessionService.invalidateSession("user-1", "session-1");
        sessionService.invalidateSession("user-1", "session-2");

        verify(redisTemplate).delete("user_session:user-1");
        verify(redisTemplate).delete("session_user:session-1");
        verify(redisTemplate).delete("session_user:session-2");
    }

    @Test
    void invalidateSessionBySessionIdLooksUpOwnerAndInvalidatesBothMappings() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("session_user:session-1")).thenReturn("user-1");
        when(valueOperations.get("user_session:user-1")).thenReturn("session-1");

        sessionService.invalidateSession("session-1");

        verify(redisTemplate).delete("user_session:user-1");
        verify(redisTemplate).delete("session_user:session-1");
    }

    @Test
    void invalidateOperationsAreNoOpsWhenDisabledOrBlank() {
        ReflectionTestUtils.setField(sessionService, "sessionEnabled", false);

        sessionService.invalidateSession("user-1", "session-1");
        sessionService.invalidateSession("session-1");

        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(any(String.class));
    }
}
