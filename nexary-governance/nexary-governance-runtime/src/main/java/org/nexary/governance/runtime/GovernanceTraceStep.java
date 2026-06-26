package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.Objects;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.governance.GovernanceIsolationReason;
import org.nexary.core.retry.RetryStopReason;

/** One low-cardinality step inside a local fault trace. */
public final class GovernanceTraceStep {
    private final GovernanceTraceStage stage;
    private final String resourceKey;
    private final GovernanceRuntimeAction action;
    private final GovernanceCallOutcome outcome;
    private final GovernanceDurationBucket durationBucket;
    private final Instant timestamp;
    private final GovernanceRejectionReason rejectionReason;
    private final GovernanceBlockReason blockReason;
    private final CancellationReason cancellationReason;
    private final RetryStopReason retryStopReason;
    private final GovernanceIsolationReason isolationReason;
    private final InstanceHealthState instanceHealthState;
    private final InstanceQuarantineReason quarantineReason;

    /** Creates a trace step with the shared runtime diagnostic fields. */
    public GovernanceTraceStep(
            GovernanceTraceStage stage,
            String resourceKey,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceDurationBucket durationBucket,
            Instant timestamp,
            GovernanceRejectionReason rejectionReason,
            GovernanceBlockReason blockReason,
            CancellationReason cancellationReason,
            RetryStopReason retryStopReason,
            GovernanceIsolationReason isolationReason,
            InstanceHealthState instanceHealthState,
            InstanceQuarantineReason quarantineReason) {
        this.stage = stage == null ? GovernanceTraceStage.GOVERNANCE : stage;
        this.resourceKey = resourceKey == null ? "custom:unknown:unknown:default" : resourceKey;
        this.action = action == null ? GovernanceRuntimeAction.EXECUTE : action;
        this.outcome = outcome == null ? GovernanceCallOutcome.NONE : outcome;
        this.durationBucket = durationBucket == null ? GovernanceDurationBucket.NOT_RUN : durationBucket;
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.rejectionReason = rejectionReason == null ? GovernanceRejectionReason.NONE : rejectionReason;
        this.blockReason = blockReason == null ? GovernanceBlockReason.NONE : blockReason;
        this.cancellationReason = cancellationReason == null ? CancellationReason.NONE : cancellationReason;
        this.retryStopReason = retryStopReason == null ? RetryStopReason.NONE : retryStopReason;
        this.isolationReason = isolationReason == null ? GovernanceIsolationReason.NONE : isolationReason;
        this.instanceHealthState = instanceHealthState == null ? InstanceHealthState.HEALTHY : instanceHealthState;
        this.quarantineReason = quarantineReason == null ? InstanceQuarantineReason.NONE : quarantineReason;
    }

    /** Creates a trace step from an existing low-cardinality runtime event. */
    public static GovernanceTraceStep fromEvent(GovernanceTraceStage stage, GovernanceRuntimeEvent event) {
        return new GovernanceTraceStep(
                stage,
                event == null ? null : event.resourceKey(),
                event == null ? null : event.action(),
                event == null ? null : event.outcome(),
                event == null ? null : event.durationBucket(),
                event == null ? null : event.timestamp(),
                event == null ? null : event.rejectionReason(),
                event == null ? null : event.blockReason(),
                event == null ? null : event.cancellationReason(),
                event == null ? null : event.retryStopReason(),
                event == null ? null : event.isolationReason(),
                event == null ? null : event.instanceHealthState(),
                event == null ? null : event.quarantineReason());
    }

    /** Returns the fixed trace stage. */
    public GovernanceTraceStage stage() {
        return stage;
    }

    /** Returns the stable governed resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the low-cardinality runtime action. */
    public GovernanceRuntimeAction action() {
        return action;
    }

    /** Returns the low-cardinality runtime outcome. */
    public GovernanceCallOutcome outcome() {
        return outcome;
    }

    /** Returns the coarse duration bucket. */
    public GovernanceDurationBucket durationBucket() {
        return durationBucket;
    }

    /** Returns when this step was recorded. */
    public Instant timestamp() {
        return timestamp;
    }

    /** Returns the low-cardinality rejection reason, if any. */
    public GovernanceRejectionReason rejectionReason() {
        return rejectionReason;
    }

    /** Returns the low-cardinality engine block reason, if any. */
    public GovernanceBlockReason blockReason() {
        return blockReason;
    }

    /** Returns the low-cardinality cancellation reason, if any. */
    public CancellationReason cancellationReason() {
        return cancellationReason;
    }

    /** Returns the low-cardinality retry-stop reason, if any. */
    public RetryStopReason retryStopReason() {
        return retryStopReason;
    }

    /** Returns the low-cardinality priority isolation reason, if any. */
    public GovernanceIsolationReason isolationReason() {
        return isolationReason;
    }

    /** Returns the local instance health state attached to this step. */
    public InstanceHealthState instanceHealthState() {
        return instanceHealthState;
    }

    /** Returns the local instance quarantine reason attached to this step. */
    public InstanceQuarantineReason quarantineReason() {
        return quarantineReason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceTraceStep)) {
            return false;
        }
        GovernanceTraceStep that = (GovernanceTraceStep) other;
        return stage == that.stage
                && resourceKey.equals(that.resourceKey)
                && action == that.action
                && outcome == that.outcome
                && durationBucket == that.durationBucket
                && timestamp.equals(that.timestamp)
                && rejectionReason == that.rejectionReason
                && blockReason == that.blockReason
                && cancellationReason == that.cancellationReason
                && retryStopReason == that.retryStopReason
                && isolationReason == that.isolationReason
                && instanceHealthState == that.instanceHealthState
                && quarantineReason == that.quarantineReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                stage,
                resourceKey,
                action,
                outcome,
                durationBucket,
                timestamp,
                rejectionReason,
                blockReason,
                cancellationReason,
                retryStopReason,
                isolationReason,
                instanceHealthState,
                quarantineReason);
    }
}
