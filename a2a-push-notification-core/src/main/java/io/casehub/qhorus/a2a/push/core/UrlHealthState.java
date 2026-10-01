package io.casehub.qhorus.a2a.push.core;

import java.time.Duration;
import java.time.Instant;

public record UrlHealthState(int failures, Instant lastFailure, Duration backoffWindow) {

    public static final Duration ZERO_BACKOFF = Duration.ZERO;

    private static final Duration[] BACKOFF_LEVELS = {
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        Duration.ofMinutes(2),
        Duration.ofMinutes(10),
        Duration.ofHours(1)
    };

    public static UrlHealthState initial(Instant failedAt) {
        return new UrlHealthState(1, failedAt, BACKOFF_LEVELS[0]);
    }

    public UrlHealthState recordFailure(Instant failedAt) {
        int next = failures + 1;
        int idx = Math.min(next - 1, BACKOFF_LEVELS.length - 1);
        return new UrlHealthState(next, failedAt, BACKOFF_LEVELS[idx]);
    }

    public boolean isWithinBackoff(Instant now) {
        return lastFailure.plus(backoffWindow).isAfter(now);
    }
}
