package org.nexary.messaging;

/** Backoff calculation mode for provider-neutral message retries. */
public enum MessageBackoffStrategy {
    /** Keep the same delay for every retry attempt. */
    FIXED,

    /** Increase retry delay by the configured multiplier and cap it at max backoff. */
    EXPONENTIAL
}
