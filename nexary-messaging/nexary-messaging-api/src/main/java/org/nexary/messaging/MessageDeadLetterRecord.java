package org.nexary.messaging;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Provider-neutral terminal failure record for a message that exhausted Nexary retry policy. */
public record MessageDeadLetterRecord(
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
    public MessageDeadLetterRecord {
        headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        status = status == null ? MessageFailureStatus.RETRY_EXHAUSTED : status;
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
}
