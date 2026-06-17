package org.nexary.messaging;

import java.time.Duration;
import java.util.Objects;

/** Provider-neutral bounded retry policy for message consumption failures. */
public final class MessageRetryPolicy {
    private final int maxAttempts;
    private final Duration initialDelay;
    private final MessageBackoffStrategy backoffStrategy;
    private final double multiplier;
    private final Duration maxBackoff;

    public MessageRetryPolicy(
            int maxAttempts,
            Duration initialDelay,
            MessageBackoffStrategy backoffStrategy,
            double multiplier,
            Duration maxBackoff) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        Duration normalizedInitialDelay = normalize(initialDelay);
        Duration normalizedMaxBackoff = normalize(maxBackoff);
        MessageBackoffStrategy normalizedBackoffStrategy =
                backoffStrategy == null ? MessageBackoffStrategy.FIXED : backoffStrategy;
        double normalizedMultiplier = multiplier < 1.0d ? 1.0d : multiplier;
        if (normalizedMaxBackoff.compareTo(normalizedInitialDelay) < 0) {
            normalizedMaxBackoff = normalizedInitialDelay;
        }
        this.maxAttempts = maxAttempts;
        this.initialDelay = normalizedInitialDelay;
        this.backoffStrategy = normalizedBackoffStrategy;
        this.multiplier = normalizedMultiplier;
        this.maxBackoff = normalizedMaxBackoff;
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

    /** Maximum number of consume attempts before terminal failure. */
    public int maxAttempts() {
        return maxAttempts;
    }

    /** Initial retry delay. */
    public Duration initialDelay() {
        return initialDelay;
    }

    /** Backoff strategy. */
    public MessageBackoffStrategy backoffStrategy() {
        return backoffStrategy;
    }

    /** Backoff multiplier for exponential strategy. */
    public double multiplier() {
        return multiplier;
    }

    /** Maximum retry backoff. */
    public Duration maxBackoff() {
        return maxBackoff;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageRetryPolicy)) {
            return false;
        }
        MessageRetryPolicy that = (MessageRetryPolicy) other;
        return maxAttempts == that.maxAttempts
                && Double.compare(that.multiplier, multiplier) == 0
                && Objects.equals(initialDelay, that.initialDelay)
                && backoffStrategy == that.backoffStrategy
                && Objects.equals(maxBackoff, that.maxBackoff);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxAttempts, initialDelay, backoffStrategy, multiplier, maxBackoff);
    }

    @Override
    public String toString() {
        return "MessageRetryPolicy[maxAttempts=" + maxAttempts
                + ", initialDelay=" + initialDelay
                + ", backoffStrategy=" + backoffStrategy
                + ", multiplier=" + multiplier
                + ", maxBackoff=" + maxBackoff
                + "]";
    }
}
