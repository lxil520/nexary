package org.nexary.messaging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Business-facing message producer.
 *
 * <p>This is the preferred API for application code. Provider-specific publishing still goes through
 * {@link MessagePublisher}; business code should normally call {@link #sendMessage(String, Object)} or
 * {@link #sendMessage(String, String, Object)}.
 */
public final class NexaryMessageProducer {
    private final ObjectProvider<MessagePublisher> publisherProvider;

    public NexaryMessageProducer(ObjectProvider<MessagePublisher> publisherProvider) {
        this.publisherProvider = publisherProvider;
    }

    /** Sends a message to a topic with an auto-generated message id. */
    public CompletionStage<MessagePublishResult> sendMessage(String topic, Object message) {
        return sendMessage(topic, null, UUID.randomUUID().toString(), message, Map.of());
    }

    /** Sends a keyed message to a topic with an auto-generated message id. */
    public CompletionStage<MessagePublishResult> sendMessage(String topic, String key, Object message) {
        return sendMessage(topic, key, UUID.randomUUID().toString(), message, Map.of());
    }

    /** Sends a keyed message with an explicit message id used by duplicate-consumption protection. */
    public CompletionStage<MessagePublishResult> sendMessage(String topic, String key, String messageId, Object message) {
        return sendMessage(topic, key, messageId, message, Map.of());
    }

    /** Sends a keyed message with an explicit message id and headers. */
    public CompletionStage<MessagePublishResult> sendMessage(
            String topic,
            String key,
            String messageId,
            Object message,
            Map<String, String> headers) {
        MessagePublisher publisher = publisherProvider.getIfAvailable();
        if (publisher == null) {
            throw new IllegalStateException("No Nexary MessagePublisher is available");
        }
        Map<String, String> effectiveHeaders = new LinkedHashMap<>();
        if (headers != null) {
            effectiveHeaders.putAll(headers);
        }
        effectiveHeaders.putIfAbsent(MessageEnvelope.MESSAGE_ID_HEADER,
                messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId);
        return publisher.publish(new MessageEnvelope<>(topic, key, message, effectiveHeaders, null, null));
    }
}
