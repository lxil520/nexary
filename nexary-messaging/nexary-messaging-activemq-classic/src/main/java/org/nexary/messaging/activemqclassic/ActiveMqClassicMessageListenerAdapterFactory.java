package org.nexary.messaging.activemqclassic;

import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageSerializer;

/** Factory for ActiveMQ Classic listener adapters that share Nexary consume policies. */
public class ActiveMqClassicMessageListenerAdapterFactory {
    private final MessageConsumeExecutor consumeExecutor;
    private final MessageSerializer serializer;
    private final NexaryObservationPublisher observationPublisher;

    public ActiveMqClassicMessageListenerAdapterFactory(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            NexaryObservationPublisher observationPublisher) {
        this.consumeExecutor = consumeExecutor;
        this.serializer = serializer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Creates a typed adapter for a consumer callback. */
    public <T> ActiveMqClassicMessageListenerAdapter<T> create(
            String consumerGroup,
            Class<T> payloadType,
            MessageConsumer<T> consumer) {
        return new ActiveMqClassicMessageListenerAdapter<>(
                consumeExecutor,
                serializer,
                payloadType,
                consumerGroup,
                consumer,
                observationPublisher);
    }
}
