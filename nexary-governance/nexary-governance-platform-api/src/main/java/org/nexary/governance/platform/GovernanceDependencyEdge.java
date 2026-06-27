package org.nexary.governance.platform;

import java.util.Map;

/** Topology edge shown by the operations console. */
public final class GovernanceDependencyEdge {
    private final String sourceKey;
    private final String targetKey;
    private final GovernanceDependencyKind kind;
    private final String resourceKey;
    private final long warningCount;
    private final long criticalCount;
    private final Map<String, String> attributes;

    /** Creates a topology edge. */
    public GovernanceDependencyEdge(
            String sourceKey,
            String targetKey,
            GovernanceDependencyKind kind,
            String resourceKey,
            long warningCount,
            long criticalCount,
            Map<String, String> attributes) {
        this.sourceKey = GovernancePlatformValidators.token(sourceKey, "sourceKey");
        this.targetKey = GovernancePlatformValidators.token(targetKey, "targetKey");
        this.kind = kind == null ? GovernanceDependencyKind.HTTP : kind;
        this.resourceKey = resourceKey == null ? "" : GovernancePlatformValidators.token(resourceKey, "resourceKey");
        this.warningCount = Math.max(0L, warningCount);
        this.criticalCount = Math.max(0L, criticalCount);
        this.attributes = GovernancePlatformValidators.attributes(attributes);
    }

    /** Returns the source service key. */
    public String sourceKey() { return sourceKey; }
    /** Returns the target service or middleware key. */
    public String targetKey() { return targetKey; }
    /** Returns the dependency kind. */
    public GovernanceDependencyKind kind() { return kind; }
    /** Returns the mapped resource key when known. */
    public String resourceKey() { return resourceKey; }
    /** Returns warning signals associated with this edge. */
    public long warningCount() { return warningCount; }
    /** Returns critical signals associated with this edge. */
    public long criticalCount() { return criticalCount; }
    /** Returns bounded low-cardinality attributes. */
    public Map<String, String> attributes() { return attributes; }
}
