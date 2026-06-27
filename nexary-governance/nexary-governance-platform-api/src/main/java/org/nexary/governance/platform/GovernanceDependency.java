package org.nexary.governance.platform;

import java.util.Map;
import java.util.Objects;

/** Low-cardinality dependency between two platform assets. */
public final class GovernanceDependency {
    private final String sourceKey;
    private final String targetKey;
    private final GovernanceDependencyKind kind;
    private final String resourceKey;
    private final Map<String, String> attributes;

    /** Creates a dependency edge. */
    public GovernanceDependency(
            String sourceKey,
            String targetKey,
            GovernanceDependencyKind kind,
            String resourceKey,
            Map<String, String> attributes) {
        this.sourceKey = GovernancePlatformValidators.token(sourceKey, "sourceKey");
        this.targetKey = GovernancePlatformValidators.token(targetKey, "targetKey");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.resourceKey = resourceKey == null ? "" : GovernancePlatformValidators.token(resourceKey, "resourceKey");
        this.attributes = GovernancePlatformValidators.attributes(attributes);
    }

    /** Returns the source asset key. */
    public String sourceKey() {
        return sourceKey;
    }

    /** Returns the target asset key. */
    public String targetKey() {
        return targetKey;
    }

    /** Returns the fixed dependency kind. */
    public GovernanceDependencyKind kind() {
        return kind;
    }

    /** Returns the mapped Nexary resource key when available. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns bounded low-cardinality attributes. */
    public Map<String, String> attributes() {
        return attributes;
    }
}
