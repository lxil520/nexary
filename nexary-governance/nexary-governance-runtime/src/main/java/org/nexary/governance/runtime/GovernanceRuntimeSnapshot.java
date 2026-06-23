package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Low-cardinality diagnostic snapshot for one local runtime resource state. */
public final class GovernanceRuntimeSnapshot {
    private final String resourceKey;
    private final String priority;
    private final GovernanceCircuitState circuitState;
    private final int windowCalls;
    private final int windowFailures;
    private final int windowSlowCalls;
    private final int consecutiveFailures;
    private final long totalRejections;
    private final GovernanceRejectionReason lastRejectionReason;
    private final Instant openUntil;

    public GovernanceRuntimeSnapshot(
            String resourceKey,
            String priority,
            GovernanceCircuitState circuitState,
            int windowCalls,
            int windowFailures,
            int windowSlowCalls,
            int consecutiveFailures,
            long totalRejections,
            GovernanceRejectionReason lastRejectionReason,
            Instant openUntil) {
        this.resourceKey = resourceKey == null ? "default:default" : resourceKey;
        this.priority = priority == null ? "normal" : priority;
        this.circuitState = circuitState == null ? GovernanceCircuitState.CLOSED : circuitState;
        this.windowCalls = Math.max(0, windowCalls);
        this.windowFailures = Math.max(0, windowFailures);
        this.windowSlowCalls = Math.max(0, windowSlowCalls);
        this.consecutiveFailures = Math.max(0, consecutiveFailures);
        this.totalRejections = Math.max(0L, totalRejections);
        this.lastRejectionReason = lastRejectionReason == null ? GovernanceRejectionReason.NONE : lastRejectionReason;
        this.openUntil = openUntil;
    }

    /** Returns the stable governed resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the request priority bucket for this resource state. */
    public String priority() {
        return priority;
    }

    /** Returns the current circuit state. */
    public GovernanceCircuitState circuitState() {
        return circuitState;
    }

    /** Returns the number of completed calls currently retained in the circuit window. */
    public int windowCalls() {
        return windowCalls;
    }

    /** Returns the number of failed completed calls currently retained in the circuit window. */
    public int windowFailures() {
        return windowFailures;
    }

    /** Returns the number of slow completed calls currently retained in the circuit window. */
    public int windowSlowCalls() {
        return windowSlowCalls;
    }

    /** Returns the current consecutive failed-call count. */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    /** Returns the total number of local governance rejections observed by this state. */
    public long totalRejections() {
        return totalRejections;
    }

    /** Returns the low-cardinality reason for the most recent local governance rejection. */
    public GovernanceRejectionReason lastRejectionReason() {
        return lastRejectionReason;
    }

    /** Returns when the open circuit may begin half-open probing, if currently known. */
    public Optional<Instant> openUntil() {
        return Optional.ofNullable(openUntil);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceRuntimeSnapshot)) {
            return false;
        }
        GovernanceRuntimeSnapshot that = (GovernanceRuntimeSnapshot) other;
        return windowCalls == that.windowCalls
                && windowFailures == that.windowFailures
                && windowSlowCalls == that.windowSlowCalls
                && consecutiveFailures == that.consecutiveFailures
                && totalRejections == that.totalRejections
                && resourceKey.equals(that.resourceKey)
                && priority.equals(that.priority)
                && circuitState == that.circuitState
                && lastRejectionReason == that.lastRejectionReason
                && Objects.equals(openUntil, that.openUntil);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resourceKey,
                priority,
                circuitState,
                windowCalls,
                windowFailures,
                windowSlowCalls,
                consecutiveFailures,
                totalRejections,
                lastRejectionReason,
                openUntil);
    }
}
