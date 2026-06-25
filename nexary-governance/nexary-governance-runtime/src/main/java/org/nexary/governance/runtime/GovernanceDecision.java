package org.nexary.governance.runtime;

import java.util.Objects;
import org.nexary.core.fault.FaultSignal;
import org.nexary.core.retry.RetrySignal;
import org.nexary.core.retry.RetryStopReason;

/** Result returned by a local governance runtime decision. */
public final class GovernanceDecision {
    private final Decision decision;
    private final FaultSignal faultSignal;
    private final RetrySignal retrySignal;

    public GovernanceDecision(Decision decision, FaultSignal faultSignal, RetrySignal retrySignal) {
        this.decision = decision == null ? Decision.ALLOWED : decision;
        this.faultSignal = faultSignal;
        this.retrySignal = retrySignal;
    }

    /** Creates an allowed decision. */
    public static GovernanceDecision allowed() {
        return new GovernanceDecision(Decision.ALLOWED, null, null);
    }

    /** Creates a rejected decision and asks upstream retry loops to stop. */
    public static GovernanceDecision rejected(Decision decision, FaultSignal faultSignal) {
        Decision safeDecision = decision == null ? Decision.REJECTED : decision;
        return new GovernanceDecision(safeDecision, faultSignal, RetrySignal.stop(retryStopReason(safeDecision)));
    }

    /** Returns true when the protected action may run. */
    public boolean isAllowed() {
        return decision == Decision.ALLOWED;
    }

    /** Returns the local decision category. */
    public Decision decision() {
        return decision;
    }

    /** Returns the normalized fault for rejected decisions. */
    public FaultSignal faultSignal() {
        return faultSignal;
    }

    /** Returns the retry signal for rejected decisions. */
    public RetrySignal retrySignal() {
        return retrySignal;
    }

    private static RetryStopReason retryStopReason(Decision decision) {
        if (decision == Decision.DEADLINE_EXPIRED) {
            return RetryStopReason.DEADLINE_EXPIRED;
        }
        if (decision == Decision.RATE_LIMITED) {
            return RetryStopReason.RATE_LIMITED;
        }
        if (decision == Decision.CONCURRENCY_LIMITED) {
            return RetryStopReason.BULKHEAD_FULL;
        }
        if (decision == Decision.CIRCUIT_OPEN) {
            return RetryStopReason.CIRCUIT_OPEN;
        }
        if (decision == Decision.DEGRADED) {
            return RetryStopReason.DEGRADED;
        }
        if (decision == Decision.CANCELLED) {
            return RetryStopReason.CANCELLED;
        }
        return RetryStopReason.REJECTED;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceDecision)) {
            return false;
        }
        GovernanceDecision that = (GovernanceDecision) other;
        return decision == that.decision
                && Objects.equals(faultSignal, that.faultSignal)
                && Objects.equals(retrySignal, that.retrySignal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(decision, faultSignal, retrySignal);
    }

    /** Local governance decision category. */
    public enum Decision {
        ALLOWED,
        DEADLINE_EXPIRED,
        RATE_LIMITED,
        CONCURRENCY_LIMITED,
        CIRCUIT_OPEN,
        DEGRADED,
        CANCELLED,
        REJECTED
    }
}
