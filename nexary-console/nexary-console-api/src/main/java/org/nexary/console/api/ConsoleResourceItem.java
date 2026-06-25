package org.nexary.console.api;

/**
 * Read-only console view of one governed resource.
 */
public final class ConsoleResourceItem {
    private final String resourceKey;
    private final String engine;
    private final String kind;
    private final String name;
    private final String provider;
    private final String operation;
    private final String trafficClass;
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
        this(resourceKey, null, kind, name, provider, operation, "online", priority, policySnapshot, runtimeSnapshot);
    }

    /**
     * Creates a resource item from bounded resource, policy, runtime, and engine fields.
     */
    public ConsoleResourceItem(
            String resourceKey,
            String engine,
            String kind,
            String name,
            String provider,
            String operation,
            String priority,
            ConsolePolicySnapshot policySnapshot,
            ConsoleRuntimeSnapshot runtimeSnapshot) {
        this(resourceKey, engine, kind, name, provider, operation, "online", priority, policySnapshot, runtimeSnapshot);
    }

    /**
     * Creates a resource item from bounded resource, traffic, policy, runtime, and engine fields.
     */
    public ConsoleResourceItem(
            String resourceKey,
            String engine,
            String kind,
            String name,
            String provider,
            String operation,
            String trafficClass,
            String priority,
            ConsolePolicySnapshot policySnapshot,
            ConsoleRuntimeSnapshot runtimeSnapshot) {
        this.resourceKey = resourceKey;
        this.engine = engine;
        this.kind = kind;
        this.name = name;
        this.provider = provider;
        this.operation = operation;
        this.trafficClass = trafficClass;
        this.priority = priority;
        this.policySnapshot = policySnapshot;
        this.runtimeSnapshot = runtimeSnapshot;
    }

    /** Returns the stable governed resource key. */
    public String getResourceKey() {
        return resourceKey;
    }

    /** Returns the governance engine used by this resource. */
    public String getEngine() {
        return engine;
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

    /** Returns the fixed low-cardinality traffic class. */
    public String getTrafficClass() {
        return trafficClass;
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
