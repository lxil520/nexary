package org.nexary.governance.runtime;

import java.util.Objects;
import org.nexary.core.governance.GovernanceResource;

/** Read-only descriptor for one local governance resource and priority bucket. */
public final class GovernanceResourceDescriptor {
    private final String resourceKey;
    private final GovernanceResource.ResourceKind kind;
    private final String name;
    private final String provider;
    private final String operation;
    private final String priority;
    private final GovernancePolicySnapshot policySnapshot;
    private final GovernanceRuntimeSnapshot runtimeSnapshot;

    /** Creates a descriptor for a resource priority bucket. */
    public GovernanceResourceDescriptor(
            String resourceKey,
            GovernanceResource.ResourceKind kind,
            String name,
            String provider,
            String operation,
            String priority,
            GovernancePolicySnapshot policySnapshot,
            GovernanceRuntimeSnapshot runtimeSnapshot) {
        this.resourceKey = resourceKey == null ? "custom:unknown:unknown:default" : resourceKey;
        this.kind = kind == null ? GovernanceResource.ResourceKind.CUSTOM : kind;
        this.name = name == null ? "unknown" : name;
        this.provider = provider == null ? "unknown" : provider;
        this.operation = operation == null ? "default" : operation;
        this.priority = priority == null ? "normal" : priority;
        this.policySnapshot = policySnapshot == null
                ? GovernancePolicySnapshot.from(GovernancePolicy.allowAll())
                : policySnapshot;
        this.runtimeSnapshot = runtimeSnapshot;
    }

    /** Returns the stable governed resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the resource kind. */
    public GovernanceResource.ResourceKind kind() {
        return kind;
    }

    /** Returns the stable resource name. */
    public String name() {
        return name;
    }

    /** Returns the bounded provider label. */
    public String provider() {
        return provider;
    }

    /** Returns the stable operation name. */
    public String operation() {
        return operation;
    }

    /** Returns the request priority bucket. */
    public String priority() {
        return priority;
    }

    /** Returns the latest policy snapshot for this resource priority bucket. */
    public GovernancePolicySnapshot policySnapshot() {
        return policySnapshot;
    }

    /** Returns the latest runtime snapshot, if the resource has local state. */
    public GovernanceRuntimeSnapshot runtimeSnapshot() {
        return runtimeSnapshot;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceResourceDescriptor)) {
            return false;
        }
        GovernanceResourceDescriptor that = (GovernanceResourceDescriptor) other;
        return resourceKey.equals(that.resourceKey)
                && kind == that.kind
                && name.equals(that.name)
                && provider.equals(that.provider)
                && operation.equals(that.operation)
                && priority.equals(that.priority)
                && policySnapshot.equals(that.policySnapshot)
                && Objects.equals(runtimeSnapshot, that.runtimeSnapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceKey, kind, name, provider, operation, priority, policySnapshot, runtimeSnapshot);
    }
}
