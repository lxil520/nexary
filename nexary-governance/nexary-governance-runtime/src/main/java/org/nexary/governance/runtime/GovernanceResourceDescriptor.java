package org.nexary.governance.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.nexary.core.governance.GovernanceResource;

/** Read-only descriptor for one local governance resource and priority bucket. */
public final class GovernanceResourceDescriptor {
    private final String resourceKey;
    private final GovernanceResource.ResourceKind kind;
    private final String name;
    private final String provider;
    private final String operation;
    private final String trafficClass;
    private final String priority;
    private final GovernanceEngine engine;
    private final GovernancePolicySnapshot policySnapshot;
    private final GovernanceRuntimeSnapshot runtimeSnapshot;
    private final List<InstanceHealthSnapshot> instanceHealthSnapshots;
    private final GovernanceCallOutcome lastTraceOutcome;
    private final GovernanceTraceStopReason lastTraceStopReason;

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
        this(
                resourceKey,
                kind,
                name,
                provider,
                operation,
                "online",
                priority,
                GovernanceEngine.LOCAL,
                policySnapshot,
                runtimeSnapshot);
    }

    /** Creates a descriptor for a resource priority bucket and governance engine. */
    public GovernanceResourceDescriptor(
            String resourceKey,
            GovernanceResource.ResourceKind kind,
            String name,
            String provider,
            String operation,
            String priority,
            GovernanceEngine engine,
            GovernancePolicySnapshot policySnapshot,
            GovernanceRuntimeSnapshot runtimeSnapshot) {
        this(resourceKey, kind, name, provider, operation, "online", priority, engine, policySnapshot, runtimeSnapshot);
    }

    /** Creates a descriptor for a resource traffic/priority bucket and governance engine. */
    public GovernanceResourceDescriptor(
            String resourceKey,
            GovernanceResource.ResourceKind kind,
            String name,
            String provider,
            String operation,
            String trafficClass,
            String priority,
            GovernanceEngine engine,
            GovernancePolicySnapshot policySnapshot,
            GovernanceRuntimeSnapshot runtimeSnapshot) {
        this(
                resourceKey,
                kind,
                name,
                provider,
                operation,
                trafficClass,
                priority,
                engine,
                policySnapshot,
                runtimeSnapshot,
                Collections.emptyList());
    }

    /** Creates a descriptor for a resource traffic/priority bucket with instance health snapshots. */
    public GovernanceResourceDescriptor(
            String resourceKey,
            GovernanceResource.ResourceKind kind,
            String name,
            String provider,
            String operation,
            String trafficClass,
            String priority,
            GovernanceEngine engine,
            GovernancePolicySnapshot policySnapshot,
            GovernanceRuntimeSnapshot runtimeSnapshot,
            List<InstanceHealthSnapshot> instanceHealthSnapshots) {
        this(
                resourceKey,
                kind,
                name,
                provider,
                operation,
                trafficClass,
                priority,
                engine,
                policySnapshot,
                runtimeSnapshot,
                instanceHealthSnapshots,
                GovernanceCallOutcome.NONE,
                GovernanceTraceStopReason.NONE);
    }

    /** Creates a descriptor with instance health snapshots and last local trace metadata. */
    public GovernanceResourceDescriptor(
            String resourceKey,
            GovernanceResource.ResourceKind kind,
            String name,
            String provider,
            String operation,
            String trafficClass,
            String priority,
            GovernanceEngine engine,
            GovernancePolicySnapshot policySnapshot,
            GovernanceRuntimeSnapshot runtimeSnapshot,
            List<InstanceHealthSnapshot> instanceHealthSnapshots,
            GovernanceCallOutcome lastTraceOutcome,
            GovernanceTraceStopReason lastTraceStopReason) {
        this.resourceKey = resourceKey == null ? "custom:unknown:unknown:default" : resourceKey;
        this.kind = kind == null ? GovernanceResource.ResourceKind.CUSTOM : kind;
        this.name = name == null ? "unknown" : name;
        this.provider = provider == null ? "unknown" : provider;
        this.operation = operation == null ? "default" : operation;
        this.trafficClass = trafficClass == null ? "online" : trafficClass;
        this.priority = priority == null ? "normal" : priority;
        this.engine = engine == null ? GovernanceEngine.LOCAL : engine;
        this.policySnapshot = policySnapshot == null
                ? GovernancePolicySnapshot.from(GovernancePolicy.allowAll())
                : policySnapshot;
        this.runtimeSnapshot = runtimeSnapshot;
        this.instanceHealthSnapshots = immutableSnapshots(instanceHealthSnapshots);
        this.lastTraceOutcome = lastTraceOutcome == null ? GovernanceCallOutcome.NONE : lastTraceOutcome;
        this.lastTraceStopReason = lastTraceStopReason == null ? GovernanceTraceStopReason.NONE : lastTraceStopReason;
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

    /** Returns the fixed low-cardinality traffic class. */
    public String trafficClass() {
        return trafficClass;
    }

    /** Returns the request priority bucket. */
    public String priority() {
        return priority;
    }

    /** Returns the low-cardinality governance engine label. */
    public GovernanceEngine engine() {
        return engine;
    }

    /** Returns the latest policy snapshot for this resource priority bucket. */
    public GovernancePolicySnapshot policySnapshot() {
        return policySnapshot;
    }

    /** Returns the latest runtime snapshot, if the resource has local state. */
    public GovernanceRuntimeSnapshot runtimeSnapshot() {
        return runtimeSnapshot;
    }

    /** Returns local instance health snapshots associated with this resource. */
    public List<InstanceHealthSnapshot> instanceHealthSnapshots() {
        return instanceHealthSnapshots;
    }

    /** Returns the latest terminal trace outcome associated with this resource. */
    public GovernanceCallOutcome lastTraceOutcome() {
        return lastTraceOutcome;
    }

    /** Returns the latest primary stop reason associated with this resource. */
    public GovernanceTraceStopReason lastTraceStopReason() {
        return lastTraceStopReason;
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
                && trafficClass.equals(that.trafficClass)
                && priority.equals(that.priority)
                && engine == that.engine
                && policySnapshot.equals(that.policySnapshot)
                && Objects.equals(runtimeSnapshot, that.runtimeSnapshot)
                && instanceHealthSnapshots.equals(that.instanceHealthSnapshots)
                && lastTraceOutcome == that.lastTraceOutcome
                && lastTraceStopReason == that.lastTraceStopReason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resourceKey,
                kind,
                name,
                provider,
                operation,
                trafficClass,
                priority,
                engine,
                policySnapshot,
                runtimeSnapshot,
                instanceHealthSnapshots,
                lastTraceOutcome,
                lastTraceStopReason);
    }

    private static List<InstanceHealthSnapshot> immutableSnapshots(List<InstanceHealthSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(snapshots));
    }
}
