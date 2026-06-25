package org.nexary.core.context;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Describes traffic identity for routing, observation, and future isolation policies. */
public final class TrafficTag {
    private final Channel channel;
    private final Priority priority;
    private final String tenant;
    private final String bizKey;
    private final Map<String, String> attributes;

    public TrafficTag(Channel channel, Priority priority, String tenant, String bizKey, Map<String, String> attributes) {
        this.channel = channel == null ? Channel.ONLINE : channel;
        this.priority = priority == null ? Priority.NORMAL : priority;
        this.tenant = normalize(tenant, "default");
        this.bizKey = normalize(bizKey, "default");
        this.attributes = attributes == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** Creates a default online traffic tag. */
    public static TrafficTag defaults() {
        return builder().build();
    }

    /** Creates a mutable builder. */
    public static Builder builder() {
        return new Builder();
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    /** Returns the traffic channel. */
    public Channel channel() {
        return channel;
    }

    /** Returns the traffic priority. */
    public Priority priority() {
        return priority;
    }

    /** Returns the tenant identifier. */
    public String tenant() {
        return tenant;
    }

    /** Returns the business key. */
    public String bizKey() {
        return bizKey;
    }

    /** Returns immutable extra traffic attributes. */
    public Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TrafficTag)) {
            return false;
        }
        TrafficTag that = (TrafficTag) other;
        return channel == that.channel
                && priority == that.priority
                && tenant.equals(that.tenant)
                && bizKey.equals(that.bizKey)
                && attributes.equals(that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channel, priority, tenant, bizKey, attributes);
    }

    @Override
    public String toString() {
        return "TrafficTag[channel=" + channel
                + ", priority=" + priority
                + ", tenant=" + tenant
                + ", bizKey=" + bizKey
                + ", attributes=" + attributes
                + ']';
    }

    /** Fixed low-cardinality traffic class. */
    public enum Channel { ONLINE, OFFLINE, BATCH, BACKGROUND }

    /** Traffic priority. */
    public enum Priority { LOW, NORMAL, HIGH, CRITICAL }

    /** Builder for TrafficTag. */
    public static final class Builder {
        private Channel channel = Channel.ONLINE;
        private Priority priority = Priority.NORMAL;
        private String tenant = "default";
        private String bizKey = "default";
        private final Map<String, String> attributes = new LinkedHashMap<>();

        public Builder channel(Channel channel) {
            this.channel = Objects.requireNonNull(channel, "channel");
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = Objects.requireNonNull(priority, "priority");
            return this;
        }

        public Builder tenant(String tenant) {
            this.tenant = tenant;
            return this;
        }

        public Builder bizKey(String bizKey) {
            this.bizKey = bizKey;
            return this;
        }

        public Builder attribute(String key, String value) {
            if (key != null && value != null) {
                attributes.put(key, value);
            }
            return this;
        }

        public TrafficTag build() {
            return new TrafficTag(channel, priority, tenant, bizKey, attributes);
        }
    }
}
