package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.List;
import org.nexary.core.governance.GovernanceContext;

/** Resolves the local policy for a governance context. */
public interface GovernancePolicyRegistry {
    /** Returns a policy for the supplied context. */
    GovernancePolicy policyFor(GovernanceContext context);

    /** Returns configured resource descriptors known by the policy registry. */
    default List<GovernanceResourceDescriptor> resources() {
        return Collections.emptyList();
    }
}
