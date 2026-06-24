package org.nexary.governance.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.RequestPriority;

/** In-memory registry keyed by resource, with optional priority-specific overrides. */
public final class LocalGovernancePolicyRegistry implements GovernancePolicyRegistry {
    private final GovernancePolicy defaultPolicy;
    private final Map<String, GovernancePolicy> policies;
    private final Map<String, GovernanceResourceDescriptor> resources;

    public LocalGovernancePolicyRegistry(GovernancePolicy defaultPolicy, Map<String, GovernancePolicy> policies) {
        this(defaultPolicy, policies, Collections.emptyMap());
    }

    public LocalGovernancePolicyRegistry(
            GovernancePolicy defaultPolicy,
            Map<String, GovernancePolicy> policies,
            Map<String, GovernanceResourceDescriptor> resources) {
        this.defaultPolicy = defaultPolicy == null ? GovernancePolicy.allowAll() : defaultPolicy;
        this.policies = policies == null ? new LinkedHashMap<>() : new LinkedHashMap<>(policies);
        this.resources = resources == null ? new LinkedHashMap<>() : new LinkedHashMap<>(resources);
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

    @Override
    public List<GovernanceResourceDescriptor> resources() {
        return new ArrayList<>(resources.values());
    }

    private static String priorityKey(GovernanceResource resource, RequestPriority priority) {
        return resource.key() + ":" + priority.name().toLowerCase();
    }

    /** Builder for LocalGovernancePolicyRegistry. */
    public static final class Builder {
        private GovernancePolicy defaultPolicy = GovernancePolicy.allowAll();
        private final Map<String, GovernancePolicy> policies = new LinkedHashMap<>();
        private final Map<String, GovernanceResourceDescriptor> resources = new LinkedHashMap<>();

        public Builder defaultPolicy(GovernancePolicy defaultPolicy) {
            this.defaultPolicy = defaultPolicy;
            return this;
        }

        public Builder policy(GovernanceResource resource, GovernancePolicy policy) {
            if (resource != null && policy != null) {
                policies.put(resource.key(), policy);
                resources.put(resource.key() + ":normal", descriptor(resource, RequestPriority.NORMAL, policy));
            }
            return this;
        }

        public Builder policy(GovernanceResource resource, RequestPriority priority, GovernancePolicy policy) {
            if (resource != null && priority != null && policy != null) {
                policies.put(priorityKey(resource, priority), policy);
                resources.put(priorityKey(resource, priority), descriptor(resource, priority, policy));
            }
            return this;
        }

        public LocalGovernancePolicyRegistry build() {
            return new LocalGovernancePolicyRegistry(defaultPolicy, policies, resources);
        }

        private static GovernanceResourceDescriptor descriptor(
                GovernanceResource resource,
                RequestPriority priority,
                GovernancePolicy policy) {
            return new GovernanceResourceDescriptor(
                    resource.key(),
                    resource.kind(),
                    resource.name(),
                    resource.provider(),
                    resource.operation(),
                    priority.name().toLowerCase(Locale.ROOT),
                    GovernancePolicySnapshot.from(policy),
                    null);
        }
    }
}
