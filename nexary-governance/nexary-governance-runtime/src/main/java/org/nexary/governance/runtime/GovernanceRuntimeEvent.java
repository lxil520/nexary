package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.Objects;
import org.nexary.core.context.CancellationReason;

/** Recent low-cardinality event recorded by the local governance runtime. */
public final class GovernanceRuntimeEvent {
    private final String resourceKey;
    private final GovernanceRuntimeAction action;
    private final GovernanceCallOutcome outcome;
    private final GovernanceRejectionReason rejectionReason;
    private final CancellationReason cancellationReason;
    private final GovernanceCircuitState circuitState;
    private final Instant timestamp;
    private final GovernanceDurationBucket durationBucket;

    /** Creates a low-cardinality runtime event. */
    public GovernanceRuntimeEvent(
            String resourceKey,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            GovernanceCircuitState circuitState,
            Instant timestamp,
            GovernanceDurationBucket durationBucket) {
        this(resourceKey, action, outcome, rejectionReason, CancellationReason.NONE, circuitState, timestamp, durationBucket);
    }

    /** Creates a low-cardinality runtime event with an explicit cancellation reason. */
    public GovernanceRuntimeEvent(
            String resourceKey,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            CancellationReason cancellationReason,
            GovernanceCircuitState circuitState,
            Instant timestamp,
            GovernanceDurationBucket durationBucket) {
        this.resourceKey = resourceKey == null ? "custom:unknown:unknown:default" : resourceKey;
        this.action = action == null ? GovernanceRuntimeAction.EXECUTE : action;
        this.outcome = outcome == null ? GovernanceCallOutcome.NONE : outcome;
        this.rejectionReason = rejectionReason == null ? GovernanceRejectionReason.NONE : rejectionReason;
        this.cancellationReason = cancellationReason == null ? CancellationReason.NONE : cancellationReason;
        this.circuitState = circuitState == null ? GovernanceCircuitState.CLOSED : circuitState;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.durationBucket = durationBucket == null ? GovernanceDurationBucket.NOT_RUN : durationBucket;
    }

    /** Returns the stable governed resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the low-cardinality action. */
    public GovernanceRuntimeAction action() {
        return action;
    }

    /** Returns the low-cardinality outcome. */
    public GovernanceCallOutcome outcome() {
        return outcome;
    }

    /** Returns the low-cardinality rejection reason. */
    public GovernanceRejectionReason rejectionReason() {
        return rejectionReason;
    }

    /** Returns the low-cardinality cancellation reason. */
    public CancellationReason cancellationReason() {
        return cancellationReason;
    }

    /** Returns the circuit state visible after this event. */
    public GovernanceCircuitState circuitState() {
        return circuitState;
    }

    /** Returns when this event was recorded. */
    public Instant timestamp() {
        return timestamp;
    }

    /** Returns the coarse duration bucket for this event. */
    public GovernanceDurationBucket durationBucket() {
        return durationBucket;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceRuntimeEvent)) {
            return false;
        }
        GovernanceRuntimeEvent that = (GovernanceRuntimeEvent) other;
        return resourceKey.equals(that.resourceKey)
                && action == that.action
                && outcome == that.outcome
                && rejectionReason == that.rejectionReason
                && cancellationReason == that.cancellationReason
                && circuitState == that.circuitState
                && timestamp.equals(that.timestamp)
                && durationBucket == that.durationBucket;
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceKey, action, outcome, rejectionReason, cancellationReason, circuitState, timestamp, durationBucket);
    }
}
