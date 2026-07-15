package com.skillsprint.service.quiz.ai;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

/**
 * Classified AI quiz generation failure. The message is built only from the
 * reason and upstream status — never from upstream response bodies, prompts,
 * or API keys — so it is always safe to log.
 */
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiQuizGenerationException extends RuntimeException {

    AiQuizGenerationFailureReason reason;

    /** Upstream HTTP status when one was received, otherwise {@code null}. */
    Integer upstreamStatus;

    /** Upstream {@code Retry-After} hint for 429 responses, otherwise {@code null}. */
    Duration retryAfter;

    public AiQuizGenerationException(AiQuizGenerationFailureReason reason) {
        this(reason, null, null);
    }

    public AiQuizGenerationException(
            AiQuizGenerationFailureReason reason,
            Integer upstreamStatus,
            Duration retryAfter
    ) {
        super("AI quiz generation failed: reason=" + reason
                + (upstreamStatus == null ? "" : ", upstreamStatus=" + upstreamStatus));
        this.reason = reason;
        this.upstreamStatus = upstreamStatus;
        this.retryAfter = retryAfter;
    }

    public boolean isRetryable() {
        return reason.isRetryable();
    }
}
