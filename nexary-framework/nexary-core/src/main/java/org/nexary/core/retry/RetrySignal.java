package org.nexary.core.retry;

import java.time.Duration;

/** Communicates retry intent across middleware boundaries. */
public record RetrySignal(RetryDecision decision, int attempts, Duration backoff, String reason) {
    public RetrySignal {
        decision = decision == null ? RetryDecision.RETRY : decision;
        attempts = Math.max(0, attempts);
        backoff = backoff == null ? Duration.ZERO : backoff;
        reason = reason == null ? "" : reason;
    }

    /** Creates a signal that allows another retry. */
    public static RetrySignal retry(int attempts, Duration backoff, String reason) {
        return new RetrySignal(RetryDecision.RETRY, attempts, backoff, reason);
    }

    /** Creates a signal that asks upstream callers to stop retrying. */
    public static RetrySignal stop(String reason) {
        return new RetrySignal(RetryDecision.STOP, 0, Duration.ZERO, reason);
    }

    /** Retry decision. */
    public enum RetryDecision { RETRY, STOP }
}
