package org.nexary.core.retry;

import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.nexary.core.context.CancellationContext;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.context.DeadlineContext;
import org.nexary.core.governance.GovernanceRejection;
import org.nexary.core.retry.RetrySignal.RetryDecision;

/** Classifies whether a retry loop should stop instead of scheduling another attempt. */
public final class RetryStopClassifier {
    private RetryStopClassifier() {
    }

    /** Returns true when the signal explicitly asks the caller to stop retrying. */
    public static boolean shouldStop(RetrySignal signal) {
        return signal != null && signal.decision() == RetryDecision.STOP;
    }

    /** Returns true when the current thread context or error should stop a retry loop. */
    public static boolean shouldStop(Throwable error) {
        return classify(error) != RetryStopReason.NONE;
    }

    /** Classifies the current thread context and error into a bounded stop reason. */
    public static RetryStopReason classify(Throwable error) {
        RetryStopReason contextReason = currentContextReason();
        if (contextReason != RetryStopReason.NONE) {
            return contextReason;
        }
        if (error instanceof GovernanceRejection) {
            return fromGovernanceReason(((GovernanceRejection) error).governanceRejectionReason());
        }
        if (error instanceof TimeoutException) {
            return RetryStopReason.TIMEOUT;
        }
        return RetryStopReason.NONE;
    }

    /** Classifies current cancellation and deadline context without looking at an exception. */
    public static RetryStopReason currentContextReason() {
        CancellationReason cancellationReason = CancellationContext.reason();
        RetryStopReason reason = fromCancellationReason(cancellationReason);
        if (reason != RetryStopReason.NONE) {
            return reason;
        }
        return DeadlineContext.expired() ? RetryStopReason.DEADLINE_EXPIRED : RetryStopReason.NONE;
    }

    /** Maps cancellation reasons to retry stop reasons without leaking ids or payloads. */
    public static RetryStopReason fromCancellationReason(CancellationReason reason) {
        if (reason == null || reason == CancellationReason.NONE) {
            return RetryStopReason.NONE;
        }
        if (reason == CancellationReason.CLIENT_DISCONNECTED) {
            return RetryStopReason.CLIENT_DISCONNECTED;
        }
        if (reason == CancellationReason.UPSTREAM_CANCELLED) {
            return RetryStopReason.UPSTREAM_CANCELLED;
        }
        if (reason == CancellationReason.DEADLINE_EXPIRED) {
            return RetryStopReason.DEADLINE_EXPIRED;
        }
        if (reason == CancellationReason.SHUTDOWN) {
            return RetryStopReason.SHUTDOWN;
        }
        return RetryStopReason.CANCELLED;
    }

    /** Maps governance rejection labels to retry stop reasons without using exception text. */
    public static RetryStopReason fromGovernanceReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return RetryStopReason.UNKNOWN;
        }
        String normalized = reason.trim().toLowerCase(Locale.ROOT);
        if ("deadline_expired".equals(normalized)) {
            return RetryStopReason.DEADLINE_EXPIRED;
        }
        if ("rate_limited".equals(normalized)) {
            return RetryStopReason.RATE_LIMITED;
        }
        if ("concurrency_limited".equals(normalized)) {
            return RetryStopReason.BULKHEAD_FULL;
        }
        if ("circuit_open".equals(normalized)) {
            return RetryStopReason.CIRCUIT_OPEN;
        }
        if ("degraded".equals(normalized)) {
            return RetryStopReason.DEGRADED;
        }
        if ("cancelled".equals(normalized)) {
            return RetryStopReason.CANCELLED;
        }
        return RetryStopReason.from(reason);
    }
}
