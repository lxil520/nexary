package org.nexary.governance.runtime;

import org.nexary.core.context.CancellationReason;
import org.nexary.core.governance.GovernanceIsolationReason;
import org.nexary.core.retry.RetryStopReason;

/** Primary low-cardinality reason why a local fault trace stopped or needs attention. */
public enum GovernanceTraceStopReason {
    /** No stop reason is known. */
    NONE,

    /** The effective deadline expired before or during work. */
    DEADLINE_EXPIRED,

    /** The request was cancelled for a non-deadline reason. */
    CANCELLED,

    /** Retry propagation was stopped by governance. */
    RETRY_STOPPED,

    /** A governance engine blocked the call. */
    BLOCKED,

    /** Local governance rejected the call. */
    REJECTED,

    /** Priority isolation stopped or downgraded the call. */
    ISOLATED,

    /** A related instance was marked as a quarantine candidate. */
    INSTANCE_QUARANTINE_CANDIDATE,

    /** The protected work failed normally. */
    FAILURE;

    /** Returns the primary stop reason represented by one low-cardinality runtime event. */
    public static GovernanceTraceStopReason fromEvent(GovernanceRuntimeEvent event) {
        if (event == null) {
            return NONE;
        }
        return fromSignals(
                event.outcome(),
                event.rejectionReason(),
                event.blockReason(),
                event.cancellationReason(),
                event.retryStopReason(),
                event.isolationReason(),
                event.instanceHealthState(),
                event.quarantineReason());
    }

    /** Returns the primary stop reason represented by one low-cardinality trace step. */
    public static GovernanceTraceStopReason fromStep(GovernanceTraceStep step) {
        if (step == null) {
            return NONE;
        }
        return fromSignals(
                step.outcome(),
                step.rejectionReason(),
                step.blockReason(),
                step.cancellationReason(),
                step.retryStopReason(),
                step.isolationReason(),
                step.instanceHealthState(),
                step.quarantineReason());
    }

    private static GovernanceTraceStopReason fromSignals(
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            GovernanceBlockReason blockReason,
            CancellationReason cancellationReason,
            RetryStopReason retryStopReason,
            GovernanceIsolationReason isolationReason,
            InstanceHealthState instanceHealthState,
            InstanceQuarantineReason quarantineReason) {
        if (cancellationReason == CancellationReason.DEADLINE_EXPIRED
                || rejectionReason == GovernanceRejectionReason.DEADLINE_EXPIRED
                || retryStopReason == RetryStopReason.DEADLINE_EXPIRED) {
            return DEADLINE_EXPIRED;
        }
        if (cancellationReason != null && cancellationReason != CancellationReason.NONE) {
            return CANCELLED;
        }
        if (retryStopReason != null && retryStopReason != RetryStopReason.NONE) {
            return RETRY_STOPPED;
        }
        if (blockReason != null && blockReason != GovernanceBlockReason.NONE) {
            return BLOCKED;
        }
        if (rejectionReason != null && rejectionReason != GovernanceRejectionReason.NONE) {
            return REJECTED;
        }
        if (isActualIsolation(isolationReason)) {
            return ISOLATED;
        }
        if (instanceHealthState == InstanceHealthState.QUARANTINE_CANDIDATE
                || (quarantineReason != null && quarantineReason != InstanceQuarantineReason.NONE)) {
            return INSTANCE_QUARANTINE_CANDIDATE;
        }
        return outcome == GovernanceCallOutcome.FAILURE ? FAILURE : NONE;
    }

    private static boolean isActualIsolation(GovernanceIsolationReason reason) {
        return reason != null
                && reason != GovernanceIsolationReason.NONE
                && reason != GovernanceIsolationReason.MIXED_TRAFFIC;
    }
}
