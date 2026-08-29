package io.casehub.qhorus.a2a.push;

import java.time.Duration;
import java.time.Instant;

record UrlHealthState(int failures, Instant lastFailure, Duration backoffWindow) {

    static final Duration ZERO_BACKOFF = Duration.ZERO;

    private static final Duration[] BACKOFF_LEVELS = {
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        Duration.ofMinutes(2),
        Duration.ofMinutes(10),
        Duration.ofHours(1)
    };

    static UrlHealthState initial(Instant failedAt) {
        return new UrlHealthState(1, failedAt, BACKOFF_LEVELS[0]);
    }

    UrlHealthState recordFailure(Instant failedAt) {
        int next = failures + 1;
        int idx = Math.min(next - 1, BACKOFF_LEVELS.length - 1);
        return new UrlHealthState(next, failedAt, BACKOFF_LEVELS[idx]);
    }

    boolean isWithinBackoff(Instant now) {
        return lastFailure.plus(backoffWindow).isAfter(now);
    }
}
