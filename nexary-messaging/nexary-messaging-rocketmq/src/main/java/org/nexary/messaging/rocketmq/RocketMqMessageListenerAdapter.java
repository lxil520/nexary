package org.nexary.messaging.rocketmq;

import java.util.Map;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessageSerializer;

/** Provider adapter that applies Nexary consume policies around RocketMQ callbacks. */
public class RocketMqMessageListenerAdapter<T> {
    private final MessageConsumeExecutor consumeExecutor;
    private final MessageSerializer serializer;
    private final Class<T> payloadType;
    private final String consumerGroup;
    private final MessageConsumer<T> consumer;
    private final NexaryObservationPublisher observationPublisher;

    public RocketMqMessageListenerAdapter(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            Class<T> payloadType,
            MessageConsumer<T> consumer) {
        this(consumeExecutor, serializer, payloadType, "default", consumer);
    }

    public RocketMqMessageListenerAdapter(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            Class<T> payloadType,
            String consumerGroup,
            MessageConsumer<T> consumer) {
        this(consumeExecutor, serializer, payloadType, consumerGroup, consumer, NexaryObservationPublisher.noop());
    }

    public RocketMqMessageListenerAdapter(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            Class<T> payloadType,
            String consumerGroup,
            MessageConsumer<T> consumer,
            NexaryObservationPublisher observationPublisher) {
        this.consumeExecutor = consumeExecutor;
        this.serializer = serializer;
        this.payloadType = payloadType;
        this.consumerGroup = consumerGroup == null || consumerGroup.isBlank() ? "default" : consumerGroup;
        this.consumer = consumer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Handles a RocketMQ-delivered message without exposing RocketMQ message types. */
    public MessageConsumeResult onMessage(String topic, String key, byte[] payload, Map<String, String> headers) {
        T deserialized = serializer.deserialize(payload, payloadType);
        MessageEnvelope<T> envelope = new MessageEnvelope<>(topic, key, deserialized, headers, null, null);
        MessageConsumeResult result = consumeExecutor.consume(envelope, consumerGroup, consumer);
        MessageObservationSupport.publish(
                observationPublisher, "consume", "rocketmq", MessageObservationSupport.outcome(result));
        if (result.status() == MessageConsumeResult.ConsumeStatus.RETRY) {
            MessageObservationSupport.publish(
                    observationPublisher,
                    "provider.consume_status",
                    "rocketmq",
                    "retry",
                    MessageObservationSupport.boundaryTags("reconsume_later"),
                    null);
        } else {
            MessageObservationSupport.publish(
                    observationPublisher,
                    "provider.consume_status",
                    "rocketmq",
                    "success",
                    MessageObservationSupport.boundaryTags("consume_success"),
                    null);
        }
        return result;
    }
}
