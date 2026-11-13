package com.alphatracker.api.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

// In-memory sliding-window-log limiter: remembers the timestamp of each admitted request per key and
// admits a new one only if fewer than `maxRequests` fall inside the last `windowMillis`. Same algorithm
// as the assistant's app/ratelimit.py. Rejected requests are NOT recorded, so hammering a limit does not
// extend the lockout. State is per-process (resets on restart, not shared across replicas), which is fine
// for a single Railway container guarding one cheap endpoint.
public class SlidingWindowRateLimiter {

    private static final int CLEANUP_EVERY = 500;

    private final int maxRequests;
    private final long windowNanos;
    private final LongSupplier nanoClock;
    private final Map<String, Deque<Long>> hits = new HashMap<>();
    private int admitsSinceCleanup = 0;

    public SlidingWindowRateLimiter(int maxRequests, long windowMillis) {
        this(maxRequests, windowMillis, System::nanoTime);
    }

    // Clock injectable so tests can move time without sleeping.
    public SlidingWindowRateLimiter(int maxRequests, long windowMillis, LongSupplier nanoClock) {
        this.maxRequests = maxRequests;
        this.windowNanos = TimeUnit.MILLISECONDS.toNanos(windowMillis);
        this.nanoClock = nanoClock;
    }

    public synchronized void admit(String key) {
        long now = nanoClock.getAsLong();
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        evictExpired(window, now);

        if (window.size() >= maxRequests) {
            long retryNanos = window.peekFirst() + windowNanos - now;
            throw new RateLimitExceededException(Math.max(1, TimeUnit.NANOSECONDS.toSeconds(retryNanos) + 1));
        }
        window.addLast(now);

        if (++admitsSinceCleanup >= CLEANUP_EVERY) {
            admitsSinceCleanup = 0;
            for (Iterator<Deque<Long>> it = hits.values().iterator(); it.hasNext();) {
                Deque<Long> d = it.next();
                evictExpired(d, now);
                if (d.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    private void evictExpired(Deque<Long> window, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= windowNanos) {
            window.removeFirst();
        }
    }
}
