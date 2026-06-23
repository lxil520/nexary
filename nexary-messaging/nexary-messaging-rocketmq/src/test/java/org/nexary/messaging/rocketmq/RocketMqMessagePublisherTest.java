package org.nexary.messaging.rocketmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernanceRejection;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessagePublishResult;
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

    @Test
    void governanceRejectionStopsBeforeCallingRocketMqTemplate() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        CapturingRocketMqTemplate template = new CapturingRocketMqTemplate();
        RocketMqMessagePublisher publisher = new RocketMqMessagePublisher(
                template,
                new DefaultStringMessageSerializer(),
                events::add,
                new RejectingGovernanceExecution("circuit_open"));
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "raw-user-topic-should-not-be-a-tag",
                "42",
                "payload",
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-governance-rejected"),
                null,
                null);

        MessagePublishResult result = publisher.publish(envelope).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(MessagePublishResult.PublishStatus.FAILED);
        assertThat(result.retrySignal()).isNotNull();
        assertThat(result.retrySignal().decision().name()).isEqualTo("STOP");
        assertThat(template.topic).isNull();
        assertThat(template.message).isNull();
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("publish", "governance.retry.stopped");
        assertThat(events).anySatisfy(event -> assertThat(event.tags())
                .containsEntry("provider", "rocketmq")
                .containsEntry("operation", "publish")
                .containsEntry("boundary", "circuit_open"));
        assertNoHighCardinalityTags(events);
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
                    "message-governance-rejected",
                    "payload");
        });
    }

    private static final class RejectingGovernanceExecution implements GovernanceExecution {
        private final String reason;

        private RejectingGovernanceExecution(String reason) {
            this.reason = reason;
        }

        @Override
        public Object execute(GovernanceContext context, Callable<?> action) {
            throw new GovernanceRejected(reason);
        }
    }

    private static final class GovernanceRejected extends RuntimeException implements GovernanceRejection {
        private final String reason;

        private GovernanceRejected(String reason) {
            this.reason = reason;
        }

        @Override
        public String governanceRejectionReason() {
            return reason;
        }
    }
}
