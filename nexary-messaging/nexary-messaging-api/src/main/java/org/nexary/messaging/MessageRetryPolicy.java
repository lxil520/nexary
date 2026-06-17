package org.nexary.messaging;

import java.time.Duration;

/** Provider-neutral bounded retry policy for message consumption failures. */
public record MessageRetryPolicy(
        int maxAttempts,
        Duration initialDelay,
        MessageBackoffStrategy backoffStrategy,
        double multiplier,
        Duration maxBackoff) {
    public MessageRetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        initialDelay = normalize(initialDelay);
        maxBackoff = normalize(maxBackoff);
        backoffStrategy = backoffStrategy == null ? MessageBackoffStrategy.FIXED : backoffStrategy;
        multiplier = multiplier < 1.0d ? 1.0d : multiplier;
        if (maxBackoff.compareTo(initialDelay) < 0) {
            maxBackoff = initialDelay;
        }
    }

    /** Default bounded policy: three attempts with fixed one-second backoff. */
    public static MessageRetryPolicy defaults() {
        return new MessageRetryPolicy(
                3,
                Duration.ofSeconds(1),
                MessageBackoffStrategy.FIXED,
                1.0d,
                Duration.ofSeconds(1));
    }

    /** Returns whether another retry is allowed after the current failed attempt. */
    public boolean allowsRetryAfter(int failedAttempt) {
        return failedAttempt < maxAttempts;
    }

    /** Calculates the bounded backoff for the next retry after the current failed attempt. */
    public Duration backoffFor(int failedAttempt) {
        Duration delay = initialDelay;
        if (backoffStrategy == MessageBackoffStrategy.EXPONENTIAL && failedAttempt > 1) {
            double factor = Math.pow(multiplier, failedAttempt - 1.0d);
            delay = Duration.ofMillis((long) Math.ceil(initialDelay.toMillis() * factor));
        }
        return delay.compareTo(maxBackoff) > 0 ? maxBackoff : delay;
    }

    private static Duration normalize(Duration value) {
        return value == null || value.isNegative() ? Duration.ZERO : value;
    }
}
