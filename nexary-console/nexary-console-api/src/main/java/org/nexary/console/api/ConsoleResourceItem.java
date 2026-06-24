package org.nexary.console.api;

/**
 * Read-only console view of one governed resource.
 */
public final class ConsoleResourceItem {
    private final String resourceKey;
    private final String kind;
    private final String name;
    private final String provider;
    private final String operation;
    private final String priority;
    private final ConsolePolicySnapshot policySnapshot;
    private final ConsoleRuntimeSnapshot runtimeSnapshot;

    /**
     * Creates a resource item from bounded resource, policy, and runtime fields.
     */
    public ConsoleResourceItem(
            String resourceKey,
            String kind,
            String name,
            String provider,
            String operation,
            String priority,
            ConsolePolicySnapshot policySnapshot,
            ConsoleRuntimeSnapshot runtimeSnapshot) {
        this.resourceKey = resourceKey;
        this.kind = kind;
        this.name = name;
        this.provider = provider;
        this.operation = operation;
        this.priority = priority;
        this.policySnapshot = policySnapshot;
        this.runtimeSnapshot = runtimeSnapshot;
    }

    /** Returns the stable governed resource key. */
    public String getResourceKey() {
        return resourceKey;
    }

    /** Returns the resource kind. */
    public String getKind() {
        return kind;
    }

    /** Returns the stable resource name. */
    public String getName() {
        return name;
    }

    /** Returns the bounded provider label. */
    public String getProvider() {
        return provider;
    }

    /** Returns the stable operation name. */
    public String getOperation() {
        return operation;
    }

    /** Returns the request priority bucket. */
    public String getPriority() {
        return priority;
    }

    /** Returns the policy summary for this resource and priority bucket. */
    public ConsolePolicySnapshot getPolicySnapshot() {
        return policySnapshot;
    }

    /** Returns the latest runtime snapshot, or null when the resource has no local state. */
    public ConsoleRuntimeSnapshot getRuntimeSnapshot() {
        return runtimeSnapshot;
    }
}
