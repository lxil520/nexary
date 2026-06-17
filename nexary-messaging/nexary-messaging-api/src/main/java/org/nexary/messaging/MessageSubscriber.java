package org.nexary.messaging;

/** Subscribes a consumer to provider-specific message delivery without leaking broker types. */
public interface MessageSubscriber {
    /** Subscribes a consumer group to a topic. */
    <T> MessageSubscription subscribe(String topic, String consumerGroup, Class<T> payloadType, MessageConsumer<T> consumer);
}
