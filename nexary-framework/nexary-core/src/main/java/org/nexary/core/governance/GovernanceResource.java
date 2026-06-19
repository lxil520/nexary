package org.nexary.core.governance;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Names a stable governance target such as an HTTP handler, a downstream call,
 * a message consumer, or a scheduled job.
 */
public final class GovernanceResource {
    private final ResourceKind kind;
    private final String name;
    private final String provider;
    private final String operation;

    /** Creates a resource with the default operation name. */
    public GovernanceResource(ResourceKind kind, String name, String provider) {
        this(kind, name, provider, "default");
    }

    /** Creates a resource with an explicit stable operation name. */
    public GovernanceResource(ResourceKind kind, String name, String provider, String operation) {
        this.kind = kind == null ? ResourceKind.CUSTOM : kind;
        this.name = normalize(name, "unknown");
        this.provider = normalize(provider, "unknown");
        this.operation = normalize(operation, "default");
    }

    /** Creates a resource for a service-level governance target. */
    public static GovernanceResource service(String name) {
        return new GovernanceResource(ResourceKind.SERVICE, name, "nexary");
    }

    /** Creates a resource for an HTTP handler or route. */
    public static GovernanceResource http(String name, String operation) {
        return new GovernanceResource(ResourceKind.HTTP, name, "nexary", operation);
    }

    /** Creates a resource for a downstream dependency call. */
    public static GovernanceResource downstream(String name, String operation) {
        return new GovernanceResource(ResourceKind.DOWNSTREAM, name, "nexary", operation);
    }

    /** Creates a resource for cache operations. */
    public static GovernanceResource cache(String name, String provider, String operation) {
        return new GovernanceResource(ResourceKind.CACHE, name, provider, operation);
    }

    /** Creates a resource for message publishing or consuming. */
    public static GovernanceResource messaging(String name, String provider) {
        return new GovernanceResource(ResourceKind.MESSAGING, name, provider);
    }

    /** Creates a resource for a job trigger or execution. */
    public static GovernanceResource job(String name, String operation) {
        return new GovernanceResource(ResourceKind.JOB, name, "nexary", operation);
    }

    /** Creates a custom resource. */
    public static GovernanceResource custom(String name, String operation) {
        return new GovernanceResource(ResourceKind.CUSTOM, name, "nexary", operation);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    /** Returns the resource kind. */
    public ResourceKind kind() {
        return kind;
    }

    /** Returns the stable resource name. */
    public String name() {
        return name;
    }

    /** Returns the provider label used for bounded observation tags. */
    public String provider() {
        return provider;
    }

    /** Returns the stable operation name. */
    public String operation() {
        return operation;
    }

    /** Returns bounded observation tags for this resource. */
    public Map<String, String> tags() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("resource_kind", kind.name().toLowerCase(Locale.ROOT));
        tags.put("resource", name);
        tags.put("provider", provider);
        tags.put("operation", operation);
        return Collections.unmodifiableMap(tags);
    }

    /** Returns a low-cardinality key for policy lookup and metrics. */
    public String key() {
        return kind.name().toLowerCase(Locale.ROOT) + ":" + name + ":" + provider + ":" + operation;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceResource)) {
            return false;
        }
        GovernanceResource that = (GovernanceResource) other;
        return kind == that.kind
                && name.equals(that.name)
                && provider.equals(that.provider)
                && operation.equals(that.operation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, name, provider, operation);
    }

    @Override
    public String toString() {
        return "GovernanceResource[kind=" + kind
                + ", name=" + name
                + ", provider=" + provider
                + ", operation=" + operation
                + ']';
    }

    /** Resource category used for policy lookup. */
    public enum ResourceKind { SERVICE, HTTP, DOWNSTREAM, CACHE, MESSAGING, JOB, CUSTOM }
}
