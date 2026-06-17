package org.nexary.messaging;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.nexary.core.context.TrafficTag;

/** Provider-neutral message envelope. */
public record MessageEnvelope<T>(
        String topic,
        String key,
        T payload,
        Map<String, String> headers,
        Instant deadline,
        TrafficTag trafficTag) {
    public MessageEnvelope {
        Objects.requireNonNull(topic, "topic");
        if (isBlank(topic)) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        trafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
    }

    /** Creates a simple envelope. */
    public static <T> MessageEnvelope<T> of(String topic, T payload) {
        return new MessageEnvelope<>(topic, null, payload, Collections.emptyMap(), null, TrafficTag.defaults());
    }

    /** Header used for provider-neutral duplicate consumption protection. */
    public static final String MESSAGE_ID_HEADER = "nexary-message-id";

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
}
