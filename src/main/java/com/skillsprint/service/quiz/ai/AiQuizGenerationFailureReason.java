package com.skillsprint.service.quiz.ai;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Why an AI quiz generation attempt failed. Retryable reasons are transient
 * (rate limits, upstream outages, malformed drafts); non-retryable reasons
 * require operator action and must fail fast without retry.
 */
@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum AiQuizGenerationFailureReason {

    /** Upstream returned HTTP 429. */
    RATE_LIMITED(true),

    /** Upstream returned 5xx, timed out, or the connection failed. */
    UPSTREAM_UNAVAILABLE(true),

    /** A response arrived but could not be parsed or failed quiz validation. */
    INVALID_AI_DRAFT(true),

    /** Upstream rejected the request (400/401/403/other 4xx) — bad key/model/request. */
    INVALID_CONFIGURATION(false),

    /** Gemini is disabled, required configuration is missing, or there is no material. */
    NOT_READY(false);

    boolean retryable;
}
