package org.nexary.core.governance;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.nexary.core.context.DeadlineContext;
import org.nexary.core.context.TrafficTag;

/**
 * Carries the governance identity for one protected execution path.
 *
 * <p>The context is intentionally small: resource, traffic tag, priority,
 * optional deadline, and low-cardinality attributes. It does not carry payload,
 * message id, cache key, token, or exception details.</p>
 */
public final class GovernanceContext {
    private static final ThreadLocal<GovernanceContext> CURRENT = new ThreadLocal<>();

    private final GovernanceResource resource;
    private final TrafficTag trafficTag;
    private final RequestPriority priority;
    private final Instant deadline;
    private final Map<String, String> attributes;

    /**
     * Creates an immutable governance context.
     *
     * <p>Null values are normalized to safe defaults. Attribute keys and values
     * must stay low-cardinality because they may be copied into later policy or
     * observation decisions.</p>
     */
    public GovernanceContext(
            GovernanceResource resource,
            TrafficTag trafficTag,
            RequestPriority priority,
            Instant deadline,
            Map<String, String> attributes) {
        this.resource = resource == null ? GovernanceResource.custom("default", "default") : resource;
        this.trafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
        this.priority = priority == null ? RequestPriority.fromTrafficTag(this.trafficTag) : priority;
        this.deadline = deadline;
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** Creates a context builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the current thread-bound governance context. */
    public static Optional<GovernanceContext> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /** Clears the current thread-bound governance context and deadline. */
    public static void clear() {
        CURRENT.remove();
        DeadlineContext.clear();
    }

    /** Runs an action with this context bound to the current thread. */
    public static <T> T callWithContext(GovernanceContext context, Callable<T> action) throws Exception {
        Objects.requireNonNull(action, "action");
        GovernanceContext previous = CURRENT.get();
        Optional<Instant> previousDeadline = DeadlineContext.current();
        GovernanceContext safeContext = context == null ? builder().build() : context;
        CURRENT.set(safeContext);
        if (safeContext.deadline != null) {
            DeadlineContext.set(safeContext.deadline);
        } else {
            DeadlineContext.clear();
        }
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            if (previousDeadline.isPresent()) {
                DeadlineContext.set(previousDeadline.get());
            } else {
                DeadlineContext.clear();
            }
        }
    }

    /** Returns the protected resource. */
    public GovernanceResource resource() {
        return resource;
    }

    /** Returns traffic identity. */
    public TrafficTag trafficTag() {
        return trafficTag;
    }

    /** Returns request priority. */
    public RequestPriority priority() {
        return priority;
    }

    /** Returns the optional deadline. */
    public Optional<Instant> deadline() {
        return Optional.ofNullable(deadline);
    }

    /** Returns low-cardinality context attributes. */
    public Map<String, String> attributes() {
        return attributes;
    }

    /** Returns true when this context has a deadline that has already passed. */
    public boolean deadlineExpired() {
        return deadline != null && !Instant.now().isBefore(deadline);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceContext)) {
            return false;
        }
        GovernanceContext that = (GovernanceContext) other;
        return resource.equals(that.resource)
                && trafficTag.equals(that.trafficTag)
                && priority == that.priority
                && Objects.equals(deadline, that.deadline)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resource, trafficTag, priority, deadline, attributes);
    }

    @Override
    public String toString() {
        return "GovernanceContext[resource=" + resource
                + ", trafficTag=" + trafficTag
                + ", priority=" + priority
                + ", deadline=" + deadline
                + ", attributes=" + attributes
                + ']';
    }

    /** Builder for GovernanceContext. */
    public static final class Builder {
        private GovernanceResource resource = GovernanceResource.custom("default", "default");
        private TrafficTag trafficTag = TrafficTag.defaults();
        private RequestPriority priority;
        private Instant deadline;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        /** Sets the stable resource protected by this context. */
        public Builder resource(GovernanceResource resource) {
            this.resource = Objects.requireNonNull(resource, "resource");
            return this;
        }

        /** Sets the traffic identity for this context. */
        public Builder trafficTag(TrafficTag trafficTag) {
            this.trafficTag = Objects.requireNonNull(trafficTag, "trafficTag");
            return this;
        }

        /** Sets the request priority explicitly instead of deriving it from the traffic tag. */
        public Builder priority(RequestPriority priority) {
            this.priority = Objects.requireNonNull(priority, "priority");
            return this;
        }

        /** Sets the latest instant when this work should still start or continue. */
        public Builder deadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        /** Adds a bounded, low-cardinality context attribute. */
        public Builder attribute(String key, String value) {
            if (key != null && value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        /** Builds an immutable governance context. */
        public GovernanceContext build() {
            return new GovernanceContext(resource, trafficTag, priority, deadline, attributes);
        }
    }
}
