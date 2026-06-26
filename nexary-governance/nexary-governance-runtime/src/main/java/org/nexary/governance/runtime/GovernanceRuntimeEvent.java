package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.Objects;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.governance.GovernanceIsolationReason;
import org.nexary.core.governance.GovernancePriority;
import org.nexary.core.governance.GovernanceTrafficClass;
import org.nexary.core.retry.RetryStopReason;

/** Recent low-cardinality event recorded by the local governance runtime. */
public final class GovernanceRuntimeEvent {
    private final String resourceKey;
    private final GovernanceTrafficClass trafficClass;
    private final GovernancePriority priority;
    private final GovernanceRuntimeAction action;
    private final GovernanceCallOutcome outcome;
    private final GovernanceRejectionReason rejectionReason;
    private final GovernanceIsolationReason isolationReason;
    private final CancellationReason cancellationReason;
    private final GovernanceEngine engine;
    private final GovernanceBlockReason blockReason;
    private final RetryStopReason retryStopReason;
    private final GovernanceCircuitState circuitState;
    private final Instant timestamp;
    private final GovernanceDurationBucket durationBucket;
    private final InstanceHealthState instanceHealthState;
    private final InstanceQuarantineReason quarantineReason;
    private final InstanceRecoveryAdvice recoveryAdvice;

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
        this(
                resourceKey,
                GovernanceTrafficClass.ONLINE,
                GovernancePriority.NORMAL,
                action,
                outcome,
                rejectionReason,
                GovernanceIsolationReason.NONE,
                cancellationReason,
                GovernanceEngine.LOCAL,
                GovernanceBlockReason.NONE,
                RetryStopReason.NONE,
                circuitState,
                timestamp,
                durationBucket);
    }

    /** Creates a low-cardinality runtime event with engine and block metadata. */
    public GovernanceRuntimeEvent(
            String resourceKey,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            CancellationReason cancellationReason,
            GovernanceEngine engine,
            GovernanceBlockReason blockReason,
            GovernanceCircuitState circuitState,
            Instant timestamp,
            GovernanceDurationBucket durationBucket) {
        this(
                resourceKey,
                GovernanceTrafficClass.ONLINE,
                GovernancePriority.NORMAL,
                action,
                outcome,
                rejectionReason,
                GovernanceIsolationReason.NONE,
                cancellationReason,
                engine,
                blockReason,
                RetryStopReason.NONE,
                circuitState,
                timestamp,
                durationBucket);
    }

    /** Creates a low-cardinality runtime event with engine, block, and retry-stop metadata. */
    public GovernanceRuntimeEvent(
            String resourceKey,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            CancellationReason cancellationReason,
            GovernanceEngine engine,
            GovernanceBlockReason blockReason,
            RetryStopReason retryStopReason,
            GovernanceCircuitState circuitState,
            Instant timestamp,
            GovernanceDurationBucket durationBucket) {
        this(
                resourceKey,
                GovernanceTrafficClass.ONLINE,
                GovernancePriority.NORMAL,
                action,
                outcome,
                rejectionReason,
                GovernanceIsolationReason.NONE,
                cancellationReason,
                engine,
                blockReason,
                retryStopReason,
                circuitState,
                timestamp,
                durationBucket);
    }

    /** Creates a low-cardinality runtime event with traffic, priority, engine, block, and retry-stop metadata. */
    public GovernanceRuntimeEvent(
            String resourceKey,
            GovernanceTrafficClass trafficClass,
            GovernancePriority priority,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            GovernanceIsolationReason isolationReason,
            CancellationReason cancellationReason,
            GovernanceEngine engine,
            GovernanceBlockReason blockReason,
            RetryStopReason retryStopReason,
            GovernanceCircuitState circuitState,
            Instant timestamp,
            GovernanceDurationBucket durationBucket) {
        this(
                resourceKey,
                trafficClass,
                priority,
                action,
                outcome,
                rejectionReason,
                isolationReason,
                cancellationReason,
                engine,
                blockReason,
                retryStopReason,
                circuitState,
                timestamp,
                durationBucket,
                InstanceHealthState.HEALTHY,
                InstanceQuarantineReason.NONE,
                InstanceRecoveryAdvice.NONE);
    }

    /** Creates a low-cardinality runtime event with instance health metadata. */
    public GovernanceRuntimeEvent(
            String resourceKey,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            GovernanceCircuitState circuitState,
            Instant timestamp,
            GovernanceDurationBucket durationBucket,
            InstanceHealthState instanceHealthState,
            InstanceQuarantineReason quarantineReason,
            InstanceRecoveryAdvice recoveryAdvice) {
        this(
                resourceKey,
                GovernanceTrafficClass.ONLINE,
                GovernancePriority.NORMAL,
                action,
                outcome,
                rejectionReason,
                GovernanceIsolationReason.NONE,
                CancellationReason.NONE,
                GovernanceEngine.LOCAL,
                GovernanceBlockReason.NONE,
                RetryStopReason.NONE,
                circuitState,
                timestamp,
                durationBucket,
                instanceHealthState,
                quarantineReason,
                recoveryAdvice);
    }

    /** Creates a low-cardinality runtime event with all diagnostic metadata. */
    public GovernanceRuntimeEvent(
            String resourceKey,
            GovernanceTrafficClass trafficClass,
            GovernancePriority priority,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            GovernanceIsolationReason isolationReason,
            CancellationReason cancellationReason,
            GovernanceEngine engine,
            GovernanceBlockReason blockReason,
            RetryStopReason retryStopReason,
            GovernanceCircuitState circuitState,
            Instant timestamp,
            GovernanceDurationBucket durationBucket,
            InstanceHealthState instanceHealthState,
            InstanceQuarantineReason quarantineReason,
            InstanceRecoveryAdvice recoveryAdvice) {
        this.resourceKey = resourceKey == null ? "custom:unknown:unknown:default" : resourceKey;
        this.trafficClass = trafficClass == null ? GovernanceTrafficClass.ONLINE : trafficClass;
        this.priority = priority == null ? GovernancePriority.NORMAL : priority;
        this.action = action == null ? GovernanceRuntimeAction.EXECUTE : action;
        this.outcome = outcome == null ? GovernanceCallOutcome.NONE : outcome;
        this.rejectionReason = rejectionReason == null ? GovernanceRejectionReason.NONE : rejectionReason;
        this.isolationReason = isolationReason == null ? GovernanceIsolationReason.NONE : isolationReason;
        this.cancellationReason = cancellationReason == null ? CancellationReason.NONE : cancellationReason;
        this.engine = engine == null ? GovernanceEngine.LOCAL : engine;
        this.blockReason = blockReason == null ? GovernanceBlockReason.NONE : blockReason;
        this.retryStopReason = retryStopReason == null ? RetryStopReason.NONE : retryStopReason;
        this.circuitState = circuitState == null ? GovernanceCircuitState.CLOSED : circuitState;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.durationBucket = durationBucket == null ? GovernanceDurationBucket.NOT_RUN : durationBucket;
        this.instanceHealthState = instanceHealthState == null ? InstanceHealthState.HEALTHY : instanceHealthState;
        this.quarantineReason = quarantineReason == null ? InstanceQuarantineReason.NONE : quarantineReason;
        this.recoveryAdvice = recoveryAdvice == null ? InstanceRecoveryAdvice.NONE : recoveryAdvice;
    }

    /** Returns the stable governed resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the fixed low-cardinality traffic class. */
    public GovernanceTrafficClass trafficClass() {
        return trafficClass;
    }

    /** Returns the fixed low-cardinality priority bucket. */
    public GovernancePriority priority() {
        return priority;
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

    /** Returns why priority isolation was applied, if any. */
    public GovernanceIsolationReason isolationReason() {
        return isolationReason;
    }

    /** Returns the low-cardinality cancellation reason. */
    public CancellationReason cancellationReason() {
        return cancellationReason;
    }

    /** Returns the low-cardinality governance engine label. */
    public GovernanceEngine engine() {
        return engine;
    }

    /** Returns the low-cardinality block reason reported by the engine. */
    public GovernanceBlockReason blockReason() {
        return blockReason;
    }

    /** Returns the low-cardinality reason another retry should not be scheduled. */
    public RetryStopReason retryStopReason() {
        return retryStopReason;
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

    /** Returns the local instance health state associated with this event. */
    public InstanceHealthState instanceHealthState() {
        return instanceHealthState;
    }

    /** Returns the local instance quarantine reason associated with this event. */
    public InstanceQuarantineReason quarantineReason() {
        return quarantineReason;
    }

    /** Returns the local instance recovery advice associated with this event. */
    public InstanceRecoveryAdvice recoveryAdvice() {
        return recoveryAdvice;
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
                && trafficClass == that.trafficClass
                && priority == that.priority
                && action == that.action
                && outcome == that.outcome
                && rejectionReason == that.rejectionReason
                && isolationReason == that.isolationReason
                && cancellationReason == that.cancellationReason
                && engine == that.engine
                && blockReason == that.blockReason
                && retryStopReason == that.retryStopReason
                && circuitState == that.circuitState
                && timestamp.equals(that.timestamp)
                && durationBucket == that.durationBucket
                && instanceHealthState == that.instanceHealthState
                && quarantineReason == that.quarantineReason
                && recoveryAdvice == that.recoveryAdvice;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resourceKey,
                trafficClass,
                priority,
                action,
                outcome,
                rejectionReason,
                isolationReason,
                cancellationReason,
                engine,
                blockReason,
                retryStopReason,
                circuitState,
                timestamp,
                durationBucket,
                instanceHealthState,
                quarantineReason,
                recoveryAdvice);
    }
}
