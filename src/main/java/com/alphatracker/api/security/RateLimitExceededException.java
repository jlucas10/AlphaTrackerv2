package com.alphatracker.api.security;

// Thrown by SlidingWindowRateLimiter when a caller is over its limit. Carries how long to wait so the
// HTTP layer can send a Retry-After header (see GlobalExceptionHandler).
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("Rate limit exceeded; retry in " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
