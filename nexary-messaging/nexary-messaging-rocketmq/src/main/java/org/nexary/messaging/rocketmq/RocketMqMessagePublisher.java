package org.nexary.messaging.rocketmq;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.core.retry.RetrySignal;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSerializer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/** RocketMQ publisher using a RocketMQTemplate bean without exposing RocketMQ types in the API. */
public class RocketMqMessagePublisher implements MessagePublisher {
    private final Object rocketMqTemplate;
    private final MessageSerializer serializer;
    private final NexaryObservationPublisher observationPublisher;

    public RocketMqMessagePublisher(Object rocketMqTemplate, MessageSerializer serializer) {
        this(rocketMqTemplate, serializer, NexaryObservationPublisher.noop());
    }

    public RocketMqMessagePublisher(
            Object rocketMqTemplate,
            MessageSerializer serializer,
            NexaryObservationPublisher observationPublisher) {
        this.rocketMqTemplate = rocketMqTemplate;
        this.serializer = serializer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public CompletionStage<MessagePublishResult> publish(MessageEnvelope<?> envelope) {
        try {
            Message<byte[]> message = createMessage(envelope);
            Method syncSend = rocketMqTemplate.getClass().getMethod("syncSend", String.class, Object.class);
            Object result = syncSend.invoke(rocketMqTemplate, envelope.topic(), message);
            MessageObservationSupport.publish(observationPublisher, "publish", "rocketmq", "success");
            return CompletableFuture.completedFuture(MessagePublishResult.success(result == null ? null : result.toString()));
        } catch (ReflectiveOperationException ex) {
            String detail = failureDetail(ex);
            MessageObservationSupport.publish(observationPublisher, "publish", "rocketmq", "failure", java.util.Map.of(), ex);
            return CompletableFuture.completedFuture(MessagePublishResult.failed(detail, RetrySignal.stop(detail)));
        }
    }

    private String failureDetail(ReflectiveOperationException ex) {
        Throwable cause = ex instanceof InvocationTargetException invocationTargetException
                ? invocationTargetException.getTargetException()
                : ex.getCause();
        if (cause != null && cause.getMessage() != null) {
            return cause.getMessage();
        }
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private Message<byte[]> createMessage(MessageEnvelope<?> envelope) {
        MessageBuilder<byte[]> builder = MessageBuilder.withPayload(serializer.serialize(envelope.payload()));
        envelope.headers().forEach((name, value) -> {
            if (value != null) {
                builder.setHeader(name, value);
            }
        });
        builder.setHeaderIfAbsent(MessageEnvelope.MESSAGE_ID_HEADER, envelope.messageId());
        if (envelope.key() != null && !envelope.key().isBlank()) {
            builder.setHeader("KEYS", envelope.key());
        }
        return builder.build();
    }
}
