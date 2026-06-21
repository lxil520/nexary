package org.nexary.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessagePublishResult;

class KafkaMessagePublisherTest {
    @Test
    void propagatesMessageIdAndHeadersToKafkaRecord() {
        CapturingKafkaTemplate template = new CapturingKafkaTemplate();
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(template, new DefaultStringMessageSerializer());
        Instant deadline = Instant.ofEpochMilli(Instant.now().plusSeconds(30).toEpochMilli());
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "orders.events",
                "42",
                "payload",
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-42", "tenant", "demo"),
                deadline,
                null);

        publisher.publish(envelope).toCompletableFuture().join();

        assertThat(template.record).isNotNull();
        assertThat(template.record.topic()).isEqualTo("orders.events");
        assertThat(template.record.key()).isEqualTo("42");
        assertThat(new String(template.record.value(), StandardCharsets.UTF_8)).isEqualTo("payload");
        assertThat(headerValue(template.record, MessageEnvelope.MESSAGE_ID_HEADER)).isEqualTo("message-42");
        assertThat(headerValue(template.record, MessageEnvelope.DEADLINE_HEADER))
                .isEqualTo(Long.toString(deadline.toEpochMilli()));
        assertThat(headerValue(template.record, "tenant")).isEqualTo("demo");
    }

    @Test
    void rejectsExpiredPublishDeadlineBeforeCallingKafkaTemplate() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        CapturingKafkaTemplate template = new CapturingKafkaTemplate();
        KafkaMessagePublisher publisher =
                new KafkaMessagePublisher(template, new DefaultStringMessageSerializer(), events::add);
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "raw-user-topic-should-not-be-a-tag",
                "42",
                "payload",
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-expired-publish"),
                Instant.now().minusMillis(1),
                null);

        MessagePublishResult result = publisher.publish(envelope).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(MessagePublishResult.PublishStatus.FAILED);
        assertThat(result.retrySignal()).isNotNull();
        assertThat(result.retrySignal().decision().name()).isEqualTo("STOP");
        assertThat(template.record).isNull();
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("governance.deadline.exceeded", "governance.retry.stopped", "publish");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("publish");
            assertThat(event.tags())
                    .containsEntry("provider", "kafka")
                    .containsEntry("outcome", "failure")
                    .containsEntry("failure_category", "timeout")
                    .containsEntry("boundary", "deadline");
        });
        assertNoHighCardinalityTags(events);
    }

    private String headerValue(ProducerRecord<String, byte[]> record, String headerName) {
        return new String(record.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8);
    }

    private static void assertNoHighCardinalityTags(List<NexaryObservationEvent> events) {
        events.forEach(event -> {
            assertThat(event.tags()).doesNotContainKeys(
                    "message_id",
                    "payload",
                    "topic",
                    "consumer_group",
                    "exception_message",
                    "stack_trace");
            assertThat(event.tags().values()).doesNotContain(
                    "raw-user-topic-should-not-be-a-tag",
                    "message-expired-publish",
                    "payload");
        });
    }

    static final class CapturingKafkaTemplate {
        private ProducerRecord<String, byte[]> record;

        public CompletableFuture<Void> send(ProducerRecord<String, byte[]> record) {
            this.record = record;
            return CompletableFuture.completedFuture(null);
        }
    }
}
