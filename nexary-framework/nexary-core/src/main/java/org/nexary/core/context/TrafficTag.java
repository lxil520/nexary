package org.nexary.core.context;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Describes traffic identity for routing, observation, and future isolation policies. */
public record TrafficTag(Channel channel, Priority priority, String tenant, String bizKey, Map<String, String> attributes) {
    public TrafficTag {
        channel = channel == null ? Channel.ONLINE : channel;
        priority = priority == null ? Priority.NORMAL : priority;
        tenant = normalize(tenant, "default");
        bizKey = normalize(bizKey, "default");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
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
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Traffic channel. */
    public enum Channel { ONLINE, OFFLINE }

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
