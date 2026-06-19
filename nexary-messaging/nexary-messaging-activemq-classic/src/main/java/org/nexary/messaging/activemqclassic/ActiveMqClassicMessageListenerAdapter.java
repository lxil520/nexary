package org.nexary.messaging.activemqclassic;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessageSerializer;

/** Provider adapter that applies Nexary consume policies around ActiveMQ Classic callbacks. */
public class ActiveMqClassicMessageListenerAdapter<T> {
    private final MessageConsumeExecutor consumeExecutor;
    private final MessageSerializer serializer;
    private final Class<T> payloadType;
    private final String consumerGroup;
    private final MessageConsumer<T> consumer;
    private final NexaryObservationPublisher observationPublisher;

    public ActiveMqClassicMessageListenerAdapter(
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

    /** Handles a JMS-delivered message without exposing JMS message types to business code. */
    public MessageConsumeResult onMessage(String topic, Message message) throws JMSException {
        MessageEnvelope<T> envelope = ActiveMqClassicMessageCodec.toEnvelope(topic, serializer, payloadType, message);
        MessageConsumeResult result = consumeExecutor.consume(envelope, consumerGroup, consumer);
        MessageObservationSupport.publish(
                observationPublisher, "consume", "activemq_classic", MessageObservationSupport.outcome(result));
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
