package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.Objects;

/** Read-only low-cardinality snapshot for one downstream instance. */
public final class InstanceHealthSnapshot {
    private final GovernanceInstanceRef instanceRef;
    private final InstanceHealthState state;
    private final InstanceQuarantineReason quarantineReason;
    private final InstanceRecoveryAdvice recoveryAdvice;
    private final int windowCalls;
    private final int failureCount;
    private final int slowCallCount;
    private final int timeoutCount;
    private final int resetCount;
    private final int serverErrorCount;
    private final double failureRatio;
    private final double slowRatio;
    private final double timeoutRatio;
    private final double skewFactor;
    private final Instant lastSignalAt;
    private final Instant lastChangedAt;

    /** Creates a snapshot from bounded detector fields. */
    public InstanceHealthSnapshot(
            GovernanceInstanceRef instanceRef,
            InstanceHealthState state,
            InstanceQuarantineReason quarantineReason,
            InstanceRecoveryAdvice recoveryAdvice,
            int windowCalls,
            int failureCount,
            int slowCallCount,
            int timeoutCount,
            int resetCount,
            int serverErrorCount,
            double failureRatio,
            double slowRatio,
            double timeoutRatio,
            double skewFactor,
            Instant lastSignalAt,
            Instant lastChangedAt) {
        this.instanceRef = instanceRef == null
                ? GovernanceInstanceRef.of("unknown-resource", "unknown-service", "unknown-instance", "unknown")
                : instanceRef;
        this.state = state == null ? InstanceHealthState.HEALTHY : state;
        this.quarantineReason = quarantineReason == null ? InstanceQuarantineReason.NONE : quarantineReason;
        this.recoveryAdvice = recoveryAdvice == null ? InstanceRecoveryAdvice.NONE : recoveryAdvice;
        this.windowCalls = Math.max(0, windowCalls);
        this.failureCount = Math.max(0, failureCount);
        this.slowCallCount = Math.max(0, slowCallCount);
        this.timeoutCount = Math.max(0, timeoutCount);
        this.resetCount = Math.max(0, resetCount);
        this.serverErrorCount = Math.max(0, serverErrorCount);
        this.failureRatio = safeRatio(failureRatio);
        this.slowRatio = safeRatio(slowRatio);
        this.timeoutRatio = safeRatio(timeoutRatio);
        this.skewFactor = Double.isNaN(skewFactor) || skewFactor < 0.0d ? 0.0d : skewFactor;
        this.lastSignalAt = lastSignalAt;
        this.lastChangedAt = lastChangedAt;
    }

    /** Returns the bounded instance reference. */
    public GovernanceInstanceRef instanceRef() {
        return instanceRef;
    }

    /** Returns the current local health state. */
    public InstanceHealthState state() {
        return state;
    }

    /** Returns the top low-cardinality reason for the state. */
    public InstanceQuarantineReason quarantineReason() {
        return quarantineReason;
    }

    /** Returns the bounded recovery advice. */
    public InstanceRecoveryAdvice recoveryAdvice() {
        return recoveryAdvice;
    }

    /** Returns retained signals in the current detector window. */
    public int windowCalls() {
        return windowCalls;
    }

    /** Returns retained failure signals. */
    public int failureCount() {
        return failureCount;
    }

    /** Returns retained slow-call signals. */
    public int slowCallCount() {
        return slowCallCount;
    }

    /** Returns retained timeout signals. */
    public int timeoutCount() {
        return timeoutCount;
    }

    /** Returns retained reset signals. */
    public int resetCount() {
        return resetCount;
    }

    /** Returns retained server-error signals. */
    public int serverErrorCount() {
        return serverErrorCount;
    }

    /** Returns retained failure ratio. */
    public double failureRatio() {
        return failureRatio;
    }

    /** Returns retained slow-call ratio. */
    public double slowRatio() {
        return slowRatio;
    }

    /** Returns retained timeout/reset ratio. */
    public double timeoutRatio() {
        return timeoutRatio;
    }

    /** Returns the largest current peer skew factor. */
    public double skewFactor() {
        return skewFactor;
    }

    /** Returns when the latest signal was recorded. */
    public Instant lastSignalAt() {
        return lastSignalAt;
    }

    /** Returns when the state last changed. */
    public Instant lastChangedAt() {
        return lastChangedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InstanceHealthSnapshot)) {
            return false;
        }
        InstanceHealthSnapshot that = (InstanceHealthSnapshot) other;
        return windowCalls == that.windowCalls
                && failureCount == that.failureCount
                && slowCallCount == that.slowCallCount
                && timeoutCount == that.timeoutCount
                && resetCount == that.resetCount
                && serverErrorCount == that.serverErrorCount
                && Double.compare(that.failureRatio, failureRatio) == 0
                && Double.compare(that.slowRatio, slowRatio) == 0
                && Double.compare(that.timeoutRatio, timeoutRatio) == 0
                && Double.compare(that.skewFactor, skewFactor) == 0
                && instanceRef.equals(that.instanceRef)
                && state == that.state
                && quarantineReason == that.quarantineReason
                && recoveryAdvice == that.recoveryAdvice
                && Objects.equals(lastSignalAt, that.lastSignalAt)
                && Objects.equals(lastChangedAt, that.lastChangedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                instanceRef,
                state,
                quarantineReason,
                recoveryAdvice,
                windowCalls,
                failureCount,
                slowCallCount,
                timeoutCount,
                resetCount,
                serverErrorCount,
                failureRatio,
                slowRatio,
                timeoutRatio,
                skewFactor,
                lastSignalAt,
                lastChangedAt);
    }

    private static double safeRatio(double value) {
        if (Double.isNaN(value) || value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }
}
