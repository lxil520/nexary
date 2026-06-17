package org.nexary.messaging.kafka;

import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageSerializer;

/** Factory for Kafka listener adapters that share retry and deduplication policies. */
public class KafkaMessageListenerAdapterFactory {
    private final MessageConsumeExecutor consumeExecutor;
    private final MessageSerializer serializer;
    private final NexaryObservationPublisher observationPublisher;

    public KafkaMessageListenerAdapterFactory(MessageConsumeExecutor consumeExecutor, MessageSerializer serializer) {
        this(consumeExecutor, serializer, NexaryObservationPublisher.noop());
    }

    public KafkaMessageListenerAdapterFactory(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            NexaryObservationPublisher observationPublisher) {
        this.consumeExecutor = consumeExecutor;
        this.serializer = serializer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Creates a typed adapter for a consumer callback. */
    public <T> KafkaMessageListenerAdapter<T> create(Class<T> payloadType, MessageConsumer<T> consumer) {
        return new KafkaMessageListenerAdapter<>(consumeExecutor, serializer, payloadType, "default", consumer, observationPublisher);
    }

    /** Creates a typed adapter for a consumer callback with a terminal failure consumer group. */
    public <T> KafkaMessageListenerAdapter<T> create(
            String consumerGroup,
            Class<T> payloadType,
            MessageConsumer<T> consumer) {
        return new KafkaMessageListenerAdapter<>(
                consumeExecutor, serializer, payloadType, consumerGroup, consumer, observationPublisher);
    }
}
