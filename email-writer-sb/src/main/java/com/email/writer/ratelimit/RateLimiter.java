package com.email.writer.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory token bucket per key.
 * NOT distributed — fine for a single instance. For multi-instance deploys
 * swap for a Redis-backed implementation.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();
    private final long capacity;
    private final long refillIntervalMs;

    public RateLimiter(
            @Value("${app.rate-limit.capacity:60}") long capacity,
            @Value("${app.rate-limit.refill-per-minute:60}") long refillPerMinute) {
        this.capacity = capacity;
        this.refillIntervalMs = refillPerMinute <= 0 ? Long.MAX_VALUE : 60_000L / refillPerMinute;
    }

    public boolean tryConsume(String key) {
        BucketState state = buckets.computeIfAbsent(key, k -> new BucketState(capacity));
        synchronized (state) {
            long now = System.currentTimeMillis();
            long elapsed = now - state.lastRefill;
            if (elapsed >= refillIntervalMs) {
                long refill = elapsed / refillIntervalMs;
                state.tokens = Math.min(capacity, state.tokens + refill);
                state.lastRefill += refill * refillIntervalMs;
            }
            if (state.tokens > 0) {
                state.tokens--;
                return true;
            }
            return false;
        }
    }

    public long getRetryAfterSeconds(String key) {
        BucketState state = buckets.get(key);
        if (state == null) return 0;
        synchronized (state) {
            if (state.tokens > 0) return 0;
            long now = System.currentTimeMillis();
            long untilNext = refillIntervalMs - (now - state.lastRefill);
            return Math.max(1, (untilNext + 999) / 1000);
        }
    }

    private static class BucketState {
        long tokens;
        long lastRefill;

        BucketState(long initial) {
            this.tokens = initial;
            this.lastRefill = System.currentTimeMillis();
        }
    }
}
