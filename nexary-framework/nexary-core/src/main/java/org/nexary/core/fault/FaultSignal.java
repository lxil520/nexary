package org.nexary.core.fault;

import java.time.Instant;
import java.util.Objects;

/** Normalized fault signal for future circuit breaking and degradation policies. */
public final class FaultSignal {
    private final FaultType type;
    private final String source;
    private final String message;
    private final Instant occurredAt;
    private final boolean transientFault;

    public FaultSignal(FaultType type, String source, String message, Instant occurredAt, boolean transientFault) {
        this.type = type == null ? FaultType.UNKNOWN : type;
        this.source = source == null ? "unknown" : source;
        this.message = message == null ? "" : message;
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        this.transientFault = transientFault;
    }

    /** Creates a transient downstream error signal. */
    public static FaultSignal downstream(String source, String message) {
        return new FaultSignal(FaultType.DOWNSTREAM_ERROR, source, message, Instant.now(), true);
    }

    /** Returns the fault category. */
    public FaultType type() {
        return type;
    }

    /** Returns the fault source. */
    public String source() {
        return source;
    }

    /** Returns the fault message. */
    public String message() {
        return message;
    }

    /** Returns when the fault occurred. */
    public Instant occurredAt() {
        return occurredAt;
    }

    /** Returns whether the fault is expected to be transient. */
    public boolean transientFault() {
        return transientFault;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FaultSignal)) {
            return false;
        }
        FaultSignal that = (FaultSignal) other;
        return transientFault == that.transientFault
                && type == that.type
                && source.equals(that.source)
                && message.equals(that.message)
                && occurredAt.equals(that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, source, message, occurredAt, transientFault);
    }

    @Override
    public String toString() {
        return "FaultSignal[type=" + type
                + ", source=" + source
                + ", message=" + message
                + ", occurredAt=" + occurredAt
                + ", transientFault=" + transientFault
                + ']';
    }

    /** Supported fault categories. */
    public enum FaultType { TIMEOUT, REJECTED, RATE_LIMITED, DEGRADED, DOWNSTREAM_ERROR, SERIALIZATION_ERROR, UNKNOWN }
}
