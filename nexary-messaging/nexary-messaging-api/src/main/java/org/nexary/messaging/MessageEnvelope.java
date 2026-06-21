package org.nexary.messaging;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.nexary.core.context.TrafficTag;

/** Provider-neutral message envelope. */
public final class MessageEnvelope<T> {
    private final String topic;
    private final String key;
    private final T payload;
    private final Map<String, String> headers;
    private final Instant deadline;
    private final TrafficTag trafficTag;

    public MessageEnvelope(
            String topic,
            String key,
            T payload,
            Map<String, String> headers,
            Instant deadline,
            TrafficTag trafficTag) {
        Objects.requireNonNull(topic, "topic");
        if (isBlank(topic)) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        this.topic = topic;
        this.key = key;
        this.payload = payload;
        this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.deadline = deadline;
        this.trafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
    }

    /** Creates a simple envelope. */
    public static <T> MessageEnvelope<T> of(String topic, T payload) {
        return new MessageEnvelope<>(topic, null, payload, Collections.emptyMap(), null, TrafficTag.defaults());
    }

    /** Header used for provider-neutral duplicate consumption protection. */
    public static final String MESSAGE_ID_HEADER = "nexary-message-id";

    /** Header used to propagate provider-neutral message deadlines across provider boundaries. */
    public static final String DEADLINE_HEADER = "nexary-deadline-epoch-millis";

    /** Message topic. */
    public String topic() {
        return topic;
    }

    /** Provider-neutral message key. */
    public String key() {
        return key;
    }

    /** Message payload. */
    public T payload() {
        return payload;
    }

    /** Immutable provider-neutral headers. */
    public Map<String, String> headers() {
        return headers;
    }

    /** Optional message deadline. */
    public Instant deadline() {
        return deadline;
    }

    /** Traffic tag associated with this envelope. */
    public TrafficTag trafficTag() {
        return trafficTag;
    }

    /** Returns the stable message id used by idempotent consumers. */
    public String messageId() {
        String messageId = headers.get(MESSAGE_ID_HEADER);
        if (!isBlank(messageId)) {
            return messageId;
        }
        return isBlank(key) ? topic + ":" + Integer.toHexString(Objects.hashCode(payload)) : topic + ":" + key;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageEnvelope)) {
            return false;
        }
        MessageEnvelope<?> that = (MessageEnvelope<?>) other;
        return Objects.equals(topic, that.topic)
                && Objects.equals(key, that.key)
                && Objects.equals(payload, that.payload)
                && Objects.equals(headers, that.headers)
                && Objects.equals(deadline, that.deadline)
                && Objects.equals(trafficTag, that.trafficTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, key, payload, headers, deadline, trafficTag);
    }

    @Override
    public String toString() {
        return "MessageEnvelope[topic=" + topic
                + ", key=" + key
                + ", payload=" + payload
                + ", headers=" + headers
                + ", deadline=" + deadline
                + ", trafficTag=" + trafficTag
                + "]";
    }
}
