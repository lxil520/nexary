package org.nexary.core.retry;

import java.time.Duration;
import java.util.Objects;

/** Communicates retry intent across middleware boundaries. */
public final class RetrySignal {
    private final RetryDecision decision;
    private final int attempts;
    private final Duration backoff;
    private final String reason;

    public RetrySignal(RetryDecision decision, int attempts, Duration backoff, String reason) {
        this.decision = decision == null ? RetryDecision.RETRY : decision;
        this.attempts = Math.max(0, attempts);
        this.backoff = backoff == null ? Duration.ZERO : backoff;
        this.reason = reason == null ? "" : reason;
    }

    /** Creates a signal that allows another retry. */
    public static RetrySignal retry(int attempts, Duration backoff, String reason) {
        return new RetrySignal(RetryDecision.RETRY, attempts, backoff, reason);
    }

    /** Creates a signal that asks upstream callers to stop retrying. */
    public static RetrySignal stop(String reason) {
        return new RetrySignal(RetryDecision.STOP, 0, Duration.ZERO, reason);
    }

    /** Creates a signal that asks upstream callers to stop retrying for a bounded reason. */
    public static RetrySignal stop(RetryStopReason reason) {
        RetryStopReason safeReason = reason == null ? RetryStopReason.UNKNOWN : reason;
        return stop(safeReason.code());
    }

    /** Returns the retry decision. */
    public RetryDecision decision() {
        return decision;
    }

    /** Returns the current attempt count. */
    public int attempts() {
        return attempts;
    }

    /** Returns the requested retry backoff. */
    public Duration backoff() {
        return backoff;
    }

    /** Returns the retry reason. */
    public String reason() {
        return reason;
    }

    /** Returns the bounded stop reason derived from {@link #reason()}. */
    public RetryStopReason stopReason() {
        return decision == RetryDecision.STOP ? RetryStopReason.from(reason) : RetryStopReason.NONE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RetrySignal)) {
            return false;
        }
        RetrySignal that = (RetrySignal) other;
        return attempts == that.attempts
                && decision == that.decision
                && backoff.equals(that.backoff)
                && reason.equals(that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, attempts, backoff, reason);
    }

    @Override
    public String toString() {
        return "RetrySignal[decision=" + decision
                + ", attempts=" + attempts
                + ", backoff=" + backoff
                + ", reason=" + reason
                + ']';
    }

    /** Retry decision. */
    public enum RetryDecision { RETRY, STOP }
}
