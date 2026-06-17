package org.nexary.core.fault;

import java.time.Instant;

/** Normalized fault signal for future circuit breaking and degradation policies. */
public record FaultSignal(FaultType type, String source, String message, Instant occurredAt, boolean transientFault) {
    public FaultSignal {
        type = type == null ? FaultType.UNKNOWN : type;
        source = source == null ? "unknown" : source;
        message = message == null ? "" : message;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /** Creates a transient downstream error signal. */
    public static FaultSignal downstream(String source, String message) {
        return new FaultSignal(FaultType.DOWNSTREAM_ERROR, source, message, Instant.now(), true);
    }

    /** Supported fault categories. */
    public enum FaultType { TIMEOUT, REJECTED, RATE_LIMITED, DEGRADED, DOWNSTREAM_ERROR, SERIALIZATION_ERROR, UNKNOWN }
}
