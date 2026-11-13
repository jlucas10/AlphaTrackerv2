package com.alphatracker.api;

import com.alphatracker.api.security.RateLimitExceededException;
import com.alphatracker.api.security.SlidingWindowRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class SlidingWindowRateLimiterTest {

    private final AtomicLong nowNanos = new AtomicLong(0);

    private SlidingWindowRateLimiter limiter(int max, long windowMillis) {
        return new SlidingWindowRateLimiter(max, windowMillis, nowNanos::get);
    }

    private void advanceSeconds(long seconds) {
        nowNanos.addAndGet(TimeUnit.SECONDS.toNanos(seconds));
    }

    @Test
    @DisplayName("admits up to the limit, rejects the next with a Retry-After, and keys are independent")
    void rejectsOverTheLimit() {
        SlidingWindowRateLimiter limiter = limiter(2, 60_000);
        limiter.admit("1.1.1.1");
        limiter.admit("1.1.1.1");

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class, () -> limiter.admit("1.1.1.1"));
        assertTrue(ex.getRetryAfterSeconds() >= 1 && ex.getRetryAfterSeconds() <= 61);

        assertDoesNotThrow(() -> limiter.admit("2.2.2.2"));
    }

    @Test
    @DisplayName("the window slides: a request is admitted again once the oldest one has aged out")
    void windowSlides() {
        SlidingWindowRateLimiter limiter = limiter(1, 60_000);
        limiter.admit("ip");
        advanceSeconds(30);
        assertThrows(RateLimitExceededException.class, () -> limiter.admit("ip"));
        advanceSeconds(31);
        assertDoesNotThrow(() -> limiter.admit("ip"));
    }

    @Test
    @DisplayName("rejected requests are not recorded, so hammering does not extend the lockout")
    void rejectionsAreNotRecorded() {
        SlidingWindowRateLimiter limiter = limiter(1, 60_000);
        limiter.admit("ip");
        for (int i = 0; i < 5; i++) {
            advanceSeconds(10);
            assertThrows(RateLimitExceededException.class, () -> limiter.admit("ip"));
        }
        advanceSeconds(11); // 61s after the one admitted request
        assertDoesNotThrow(() -> limiter.admit("ip"));
    }
}
