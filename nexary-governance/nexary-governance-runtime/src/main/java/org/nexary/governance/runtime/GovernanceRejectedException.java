package org.nexary.governance.runtime;

import java.util.Locale;
import org.nexary.core.governance.GovernanceRejection;

/** Raised when local governance rejects a call and no fallback was supplied. */
public class GovernanceRejectedException extends RuntimeException implements GovernanceRejection {
    private final GovernanceDecision decision;

    public GovernanceRejectedException(GovernanceDecision decision) {
        super(decision == null ? "governance rejected call" : "governance rejected call: " + decision.decision());
        this.decision = decision;
    }

    /** Returns the rejection decision. */
    public GovernanceDecision decision() {
        return decision;
    }

    @Override
    public String governanceRejectionReason() {
        return decision == null ? "unknown" : decision.decision().name().toLowerCase(Locale.ROOT);
    }
}
