package org.nexary.messaging.rocketmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageEnvelope;
import org.springframework.messaging.Message;

class RocketMqMessagePublisherTest {
    @Test
    void propagatesMessageIdAndHeadersToSpringMessage() {
        CapturingRocketMqTemplate template = new CapturingRocketMqTemplate();
        RocketMqMessagePublisher publisher = new RocketMqMessagePublisher(template, new DefaultStringMessageSerializer());
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "orders.events",
                "42",
                "payload",
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-42", "tenant", "demo"),
                null,
                null);

        publisher.publish(envelope).toCompletableFuture().join();

        assertThat(template.topic).isEqualTo("orders.events");
        assertThat(template.message).isNotNull();
        assertThat(new String(template.message.getPayload())).isEqualTo("payload");
        assertThat(template.message.getHeaders().get(MessageEnvelope.MESSAGE_ID_HEADER)).isEqualTo("message-42");
        assertThat(template.message.getHeaders().get("tenant")).isEqualTo("demo");
        assertThat(template.message.getHeaders().get("KEYS")).isEqualTo("42");
    }

    static final class CapturingRocketMqTemplate {
        private String topic;
        private Message<byte[]> message;

        public String syncSend(String topic, Object message) {
            this.topic = topic;
            this.message = (Message<byte[]>) message;
            return "ok";
        }
    }
}
