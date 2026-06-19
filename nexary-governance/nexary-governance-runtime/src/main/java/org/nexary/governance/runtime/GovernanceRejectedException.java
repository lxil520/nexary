package org.nexary.governance.runtime;

/** Raised when local governance rejects a call and no fallback was supplied. */
public class GovernanceRejectedException extends RuntimeException {
    private final GovernanceDecision decision;

    public GovernanceRejectedException(GovernanceDecision decision) {
        super(decision == null ? "governance rejected call" : "governance rejected call: " + decision.decision());
        this.decision = decision;
    }

    /** Returns the rejection decision. */
    public GovernanceDecision decision() {
        return decision;
    }
}
