package com.logstream.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.logstream.exception.RateLimitExceededException;

/**
 * Lightweight in-memory, fixed-window rate limiter for the log ingestion hot path.
 * Keyed per ingestion token, so the window map stays bounded by the number of real
 * tokens. Single-instance only (per-JVM counters); acceptable for the current
 * single-instance MVP, matching {@link ManagementRateLimitFilter}.
 */
@Component
public class IngestionRateLimiter {

    private final int requestsPerMinute;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public IngestionRateLimiter(
            @Value("${app.rate-limit.ingestion-requests-per-minute:120}") int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public void check(String key) {
        if (requestsPerMinute <= 0 || key == null) {
            return;
        }

        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.compute(key, (ignored, existing) -> {
            if (existing == null || existing.minute != minute) {
                return new Window(minute);
            }
            return existing;
        });

        if (window.count.incrementAndGet() > requestsPerMinute) {
            throw new RateLimitExceededException(
                    "Too many log events for this ingestion token. Try again in a minute.");
        }
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long minute) {
            this.minute = minute;
        }
    }
}
