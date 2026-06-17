package org.nexary.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageEnvelope;

class KafkaMessagePublisherTest {
    @Test
    void propagatesMessageIdAndHeadersToKafkaRecord() {
        CapturingKafkaTemplate template = new CapturingKafkaTemplate();
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(template, new DefaultStringMessageSerializer());
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "orders.events",
                "42",
                "payload",
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-42", "tenant", "demo"),
                null,
                null);

        publisher.publish(envelope).toCompletableFuture().join();

        assertThat(template.record).isNotNull();
        assertThat(template.record.topic()).isEqualTo("orders.events");
        assertThat(template.record.key()).isEqualTo("42");
        assertThat(new String(template.record.value(), StandardCharsets.UTF_8)).isEqualTo("payload");
        assertThat(headerValue(template.record, MessageEnvelope.MESSAGE_ID_HEADER)).isEqualTo("message-42");
        assertThat(headerValue(template.record, "tenant")).isEqualTo("demo");
    }

    private String headerValue(ProducerRecord<String, byte[]> record, String headerName) {
        return new String(record.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8);
    }

    static final class CapturingKafkaTemplate {
        private ProducerRecord<String, byte[]> record;

        public CompletableFuture<Void> send(ProducerRecord<String, byte[]> record) {
            this.record = record;
            return CompletableFuture.completedFuture(null);
        }
    }
}
