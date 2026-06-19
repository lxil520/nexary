package org.nexary.governance.runtime;

import org.nexary.core.governance.GovernanceContext;

/** Resolves the local policy for a governance context. */
public interface GovernancePolicyRegistry {
    /** Returns a policy for the supplied context. */
    GovernancePolicy policyFor(GovernanceContext context);
}
