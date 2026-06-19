package org.nexary.governance.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.RequestPriority;

/** In-memory registry keyed by resource, with optional priority-specific overrides. */
public final class LocalGovernancePolicyRegistry implements GovernancePolicyRegistry {
    private final GovernancePolicy defaultPolicy;
    private final Map<String, GovernancePolicy> policies;

    public LocalGovernancePolicyRegistry(GovernancePolicy defaultPolicy, Map<String, GovernancePolicy> policies) {
        this.defaultPolicy = defaultPolicy == null ? GovernancePolicy.allowAll() : defaultPolicy;
        this.policies = policies == null ? new LinkedHashMap<>() : new LinkedHashMap<>(policies);
    }

    /** Creates a registry builder. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public GovernancePolicy policyFor(GovernanceContext context) {
        if (context == null) {
            return defaultPolicy;
        }
        GovernancePolicy byPriority = policies.get(priorityKey(context.resource(), context.priority()));
        if (byPriority != null) {
            return byPriority;
        }
        GovernancePolicy byResource = policies.get(context.resource().key());
        return byResource == null ? defaultPolicy : byResource;
    }

    private static String priorityKey(GovernanceResource resource, RequestPriority priority) {
        return resource.key() + ":" + priority.name().toLowerCase();
    }

    /** Builder for LocalGovernancePolicyRegistry. */
    public static final class Builder {
        private GovernancePolicy defaultPolicy = GovernancePolicy.allowAll();
        private final Map<String, GovernancePolicy> policies = new LinkedHashMap<>();

        public Builder defaultPolicy(GovernancePolicy defaultPolicy) {
            this.defaultPolicy = defaultPolicy;
            return this;
        }

        public Builder policy(GovernanceResource resource, GovernancePolicy policy) {
            if (resource != null && policy != null) {
                policies.put(resource.key(), policy);
            }
            return this;
        }

        public Builder policy(GovernanceResource resource, RequestPriority priority, GovernancePolicy policy) {
            if (resource != null && priority != null && policy != null) {
                policies.put(priorityKey(resource, priority), policy);
            }
            return this;
        }

        public LocalGovernancePolicyRegistry build() {
            return new LocalGovernancePolicyRegistry(defaultPolicy, policies);
        }
    }
}
