package com.skillsprint.service.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skillsprint.configuration.ratelimit.RateLimitProperties;
import com.skillsprint.exception.AppException;
import com.skillsprint.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Test
    void checkPaymentCreateThrowsWhenLimitExceeded() {
        RateLimitService service = new RateLimitService(
                redisTemplate,
                properties(true, new RateLimitProperties.Rule(1, 60))
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        AppException exception = assertThrows(
                AppException.class,
                () -> service.checkPaymentCreate("user-1")
        );

        assertEquals(ErrorCode.RATE_LIMIT_EXCEEDED, exception.getErrorCode());
    }

    @Test
    void checkPaymentCreateDoesNotUseRedisWhenDisabled() {
        RateLimitService service = new RateLimitService(
                redisTemplate,
                properties(false, new RateLimitProperties.Rule(1, 60))
        );

        service.checkPaymentCreate("user-1");

        verify(redisTemplate, never()).opsForValue();
    }

    private RateLimitProperties properties(boolean enabled, RateLimitProperties.Rule paymentCreateRule) {
        return new RateLimitProperties(
                enabled,
                new RateLimitProperties.Rule(5, 300),
                new RateLimitProperties.Rule(3, 3600),
                new RateLimitProperties.Rule(3, 900),
                new RateLimitProperties.Rule(3, 900),
                paymentCreateRule
        );
    }
}
