package org.nexary.messaging.rocketmq;

import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageSerializer;

/** Factory for RocketMQ listener adapters that share retry and deduplication policies. */
public class RocketMqMessageListenerAdapterFactory {
    private final MessageConsumeExecutor consumeExecutor;
    private final MessageSerializer serializer;
    private final NexaryObservationPublisher observationPublisher;

    public RocketMqMessageListenerAdapterFactory(MessageConsumeExecutor consumeExecutor, MessageSerializer serializer) {
        this(consumeExecutor, serializer, NexaryObservationPublisher.noop());
    }

    public RocketMqMessageListenerAdapterFactory(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            NexaryObservationPublisher observationPublisher) {
        this.consumeExecutor = consumeExecutor;
        this.serializer = serializer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Creates a typed adapter for a consumer callback. */
    public <T> RocketMqMessageListenerAdapter<T> create(Class<T> payloadType, MessageConsumer<T> consumer) {
        return new RocketMqMessageListenerAdapter<>(
                consumeExecutor, serializer, payloadType, "default", consumer, observationPublisher);
    }

    /** Creates a typed adapter for a consumer callback with a terminal failure consumer group. */
    public <T> RocketMqMessageListenerAdapter<T> create(
            String consumerGroup,
            Class<T> payloadType,
            MessageConsumer<T> consumer) {
        return new RocketMqMessageListenerAdapter<>(
                consumeExecutor, serializer, payloadType, consumerGroup, consumer, observationPublisher);
    }
}
