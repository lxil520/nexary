package org.nexary.messaging.kafka;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.nexary.core.retry.RetrySignal;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSerializer;

/** Kafka publisher using a Spring KafkaTemplate bean without exposing Kafka types in the API. */
public class KafkaMessagePublisher implements MessagePublisher {
    private final Object kafkaTemplate;
    private final MessageSerializer serializer;
    private final NexaryObservationPublisher observationPublisher;

    public KafkaMessagePublisher(Object kafkaTemplate, MessageSerializer serializer) {
        this(kafkaTemplate, serializer, NexaryObservationPublisher.noop());
    }

    public KafkaMessagePublisher(
            Object kafkaTemplate,
            MessageSerializer serializer,
            NexaryObservationPublisher observationPublisher) {
        this.kafkaTemplate = kafkaTemplate;
        this.serializer = serializer;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public CompletionStage<MessagePublishResult> publish(MessageEnvelope<?> envelope) {
        try {
            ProducerRecord<String, byte[]> record = createRecord(envelope);
            Method send = kafkaTemplate.getClass().getMethod("send", ProducerRecord.class);
            Object result = send.invoke(kafkaTemplate, record);
            if (result instanceof CompletableFuture<?>) {
                CompletableFuture<?> future = (CompletableFuture<?>) result;
                return future.handle((ignored, error) -> {
                    MessagePublishResult publishResult = error == null
                            ? MessagePublishResult.success(null)
                            : MessagePublishResult.failed(
                                    error.getMessage(),
                                    RetrySignal.retry(1, java.time.Duration.ZERO, error.getMessage()));
                    MessageObservationSupport.publish(
                            observationPublisher,
                            "publish",
                            "kafka",
                            MessageObservationSupport.outcome(publishResult),
                            Collections.emptyMap(),
                            error);
                    return publishResult;
                });
            }
            MessageObservationSupport.publish(observationPublisher, "publish", "kafka", "success");
            return CompletableFuture.completedFuture(MessagePublishResult.success(null));
        } catch (ReflectiveOperationException ex) {
            MessageObservationSupport.publish(observationPublisher, "publish", "kafka", "failure", Collections.emptyMap(), ex);
            return CompletableFuture.completedFuture(MessagePublishResult.failed(ex.getMessage(), RetrySignal.stop(ex.getMessage())));
        }
    }

    private ProducerRecord<String, byte[]> createRecord(MessageEnvelope<?> envelope) {
        RecordHeaders headers = new RecordHeaders();
        envelope.headers().forEach((name, value) -> {
            if (value != null) {
                headers.add(name, value.getBytes(StandardCharsets.UTF_8));
            }
        });
        headers.add(MessageEnvelope.MESSAGE_ID_HEADER, envelope.messageId().getBytes(StandardCharsets.UTF_8));
        return new ProducerRecord<>(envelope.topic(), null, envelope.key(), serializer.serialize(envelope.payload()), headers);
    }
}
