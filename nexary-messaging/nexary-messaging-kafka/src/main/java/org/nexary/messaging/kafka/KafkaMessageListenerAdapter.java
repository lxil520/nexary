package org.nexary.messaging.kafka;

import java.util.Map;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessageSerializer;

/** Provider adapter that applies Nexary consume policies around Kafka listener callbacks. */
public class KafkaMessageListenerAdapter<T> {
    private final MessageConsumeExecutor consumeExecutor;
    private final MessageSerializer serializer;
    private final Class<T> payloadType;
    private final String consumerGroup;
    private final MessageConsumer<T> consumer;
    private final NexaryObservationPublisher observationPublisher;

    public KafkaMessageListenerAdapter(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            Class<T> payloadType,
            MessageConsumer<T> consumer) {
        this(consumeExecutor, serializer, payloadType, "default", consumer);
    }

    public KafkaMessageListenerAdapter(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            Class<T> payloadType,
            String consumerGroup,
            MessageConsumer<T> consumer) {
        this(consumeExecutor, serializer, payloadType, consumerGroup, consumer, NexaryObservationPublisher.noop());
    }

    public KafkaMessageListenerAdapter(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            Class<T> payloadType,
            String consumerGroup,
            MessageConsumer<T> consumer,
            NexaryObservationPublisher observationPublisher) {
        this.consumeExecutor = consumeExecutor;
        this.serializer = serializer;
        this.payloadType = payloadType;
        this.consumerGroup = isBlank(consumerGroup) ? "default" : consumerGroup;
        this.consumer = consumer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Handles a Kafka-delivered message without exposing Kafka consumer record types. */
    public MessageConsumeResult onMessage(String topic, String key, byte[] payload, Map<String, String> headers) {
        T deserialized = serializer.deserialize(payload, payloadType);
        MessageEnvelope<T> envelope = new MessageEnvelope<>(topic, key, deserialized, headers, null, null);
        MessageConsumeResult result = consumeExecutor.consume(envelope, consumerGroup, consumer);
        MessageObservationSupport.publish(
                observationPublisher, "consume", "kafka", MessageObservationSupport.outcome(result));
        if (result.status() == MessageConsumeResult.ConsumeStatus.RETRY) {
            MessageObservationSupport.publish(
                    observationPublisher,
                    "provider.seek",
                    "kafka",
                    "retry",
                    MessageObservationSupport.boundaryTags("seek_current"),
                    null);
        } else {
            MessageObservationSupport.publish(
                    observationPublisher,
                    "provider.commit",
                    "kafka",
                    "success",
                    MessageObservationSupport.boundaryTags("commit_offset"),
                    null);
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
