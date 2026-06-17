package org.nexary.messaging;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral terminal failure record for a message that exhausted Nexary retry policy. */
public final class MessageDeadLetterRecord {
    private final String messageId;
    private final String topic;
    private final String key;
    private final String consumerGroup;
    private final Object payload;
    private final Map<String, String> headers;
    private final int attempts;
    private final MessageFailureStatus status;
    private final String errorType;
    private final String errorMessage;
    private final Instant occurredAt;

    public MessageDeadLetterRecord(
            String messageId,
            String topic,
            String key,
            String consumerGroup,
            Object payload,
            Map<String, String> headers,
            int attempts,
            MessageFailureStatus status,
            String errorType,
            String errorMessage,
            Instant occurredAt) {
        this.messageId = messageId;
        this.topic = topic;
        this.key = key;
        this.consumerGroup = consumerGroup;
        this.payload = payload;
        this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.attempts = attempts;
        this.status = status == null ? MessageFailureStatus.RETRY_EXHAUSTED : status;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /** Builds a retry-exhausted record from the failed envelope and error. */
    public static MessageDeadLetterRecord retryExhausted(
            MessageEnvelope<?> envelope,
            String consumerGroup,
            int attempts,
            Throwable error) {
        return new MessageDeadLetterRecord(
                envelope.messageId(),
                envelope.topic(),
                envelope.key(),
                consumerGroup,
                envelope.payload(),
                envelope.headers(),
                attempts,
                MessageFailureStatus.RETRY_EXHAUSTED,
                error == null ? null : error.getClass().getName(),
                error == null ? null : error.getMessage(),
                Instant.now());
    }

    /** Message id that reached terminal failure. */
    public String messageId() {
        return messageId;
    }

    /** Source topic. */
    public String topic() {
        return topic;
    }

    /** Provider-neutral message key. */
    public String key() {
        return key;
    }

    /** Consumer group that handled the failed message. */
    public String consumerGroup() {
        return consumerGroup;
    }

    /** Original payload. */
    public Object payload() {
        return payload;
    }

    /** Immutable provider-neutral headers. */
    public Map<String, String> headers() {
        return headers;
    }

    /** Number of failed attempts. */
    public int attempts() {
        return attempts;
    }

    /** Terminal failure status. */
    public MessageFailureStatus status() {
        return status;
    }

    /** Exception type, when available. */
    public String errorType() {
        return errorType;
    }

    /** Exception message, when available. */
    public String errorMessage() {
        return errorMessage;
    }

    /** Time the terminal failure record was created. */
    public Instant occurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageDeadLetterRecord)) {
            return false;
        }
        MessageDeadLetterRecord that = (MessageDeadLetterRecord) other;
        return attempts == that.attempts
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(topic, that.topic)
                && Objects.equals(key, that.key)
                && Objects.equals(consumerGroup, that.consumerGroup)
                && Objects.equals(payload, that.payload)
                && Objects.equals(headers, that.headers)
                && status == that.status
                && Objects.equals(errorType, that.errorType)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(occurredAt, that.occurredAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                messageId,
                topic,
                key,
                consumerGroup,
                payload,
                headers,
                attempts,
                status,
                errorType,
                errorMessage,
                occurredAt);
    }

    @Override
    public String toString() {
        return "MessageDeadLetterRecord[messageId=" + messageId
                + ", topic=" + topic
                + ", key=" + key
                + ", consumerGroup=" + consumerGroup
                + ", payload=" + payload
                + ", headers=" + headers
                + ", attempts=" + attempts
                + ", status=" + status
                + ", errorType=" + errorType
                + ", errorMessage=" + errorMessage
                + ", occurredAt=" + occurredAt
                + "]";
    }
}
