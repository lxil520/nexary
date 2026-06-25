package org.nexary.core.retry;

import java.util.Locale;

/** Low-cardinality reason for stopping a retry chain. */
public enum RetryStopReason {
    /** No terminal retry stop reason is known. */
    NONE,

    /** The request deadline expired before another attempt should run. */
    DEADLINE_EXPIRED,

    /** The current request was cancelled before another attempt should run. */
    CANCELLED,

    /** The client disconnected before another attempt should run. */
    CLIENT_DISCONNECTED,

    /** An upstream caller cancelled the request before another attempt should run. */
    UPSTREAM_CANCELLED,

    /** A shutdown cancelled the request before another attempt should run. */
    SHUTDOWN,

    /** A rate limit rejected the attempt. */
    RATE_LIMITED,

    /** A concurrency limit or bulkhead rejected the attempt. */
    BULKHEAD_FULL,

    /** An open circuit rejected the attempt. */
    CIRCUIT_OPEN,

    /** A degraded policy routed the request away from normal execution. */
    DEGRADED,

    /** The configured retry budget was exhausted. */
    RETRY_EXHAUSTED,

    /** The attempt timed out. */
    TIMEOUT,

    /** A governance rejection stopped retries but did not map to a more specific reason. */
    REJECTED,

    /** The terminal reason is intentionally unknown. */
    UNKNOWN;

    /** Returns the stable lowercase code used in retry signals and diagnostics. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Maps raw provider or legacy reason text to a bounded retry stop reason. */
    public static RetryStopReason from(String value) {
        if (value == null || value.trim().isEmpty()) {
            return NONE;
        }
        String normalized = value.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        if ("CONCURRENCY_LIMITED".equals(normalized) || "BULKHEAD_REJECTED".equals(normalized)) {
            return BULKHEAD_FULL;
        }
        if ("DEADLETTER".equals(normalized) || normalized.startsWith("MESSAGE_DEAD_LETTERED")) {
            return RETRY_EXHAUSTED;
        }
        if (normalized.contains("DEADLINE")) {
            return DEADLINE_EXPIRED;
        }
        if (normalized.contains("CLIENT_DISCONNECTED")) {
            return CLIENT_DISCONNECTED;
        }
        if (normalized.contains("UPSTREAM_CANCELLED")) {
            return UPSTREAM_CANCELLED;
        }
        if (normalized.contains("CANCELLED")) {
            return CANCELLED;
        }
        if (normalized.contains("TIMEOUT") || normalized.contains("TIMED_OUT")) {
            return TIMEOUT;
        }
        if (normalized.contains("CIRCUIT_OPEN")) {
            return CIRCUIT_OPEN;
        }
        if (normalized.contains("RATE_LIMIT")) {
            return RATE_LIMITED;
        }
        if (normalized.contains("BULKHEAD") || normalized.contains("CONCURRENCY")) {
            return BULKHEAD_FULL;
        }
        if (normalized.contains("DEGRADED")) {
            return DEGRADED;
        }
        if (normalized.contains("EXHAUST")) {
            return RETRY_EXHAUSTED;
        }
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
