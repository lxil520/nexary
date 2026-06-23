package org.nexary.messaging.activemqclassic;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.jms.BytesMessage;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.Session;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.Test;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernanceRejection;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.InMemoryMessageDeadLetterPublisher;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessageRetryPolicy;

class ActiveMqClassicMessageListenerAdapterTest {
    @Test
    void preservesMessageIdAndHeadersWithoutExposingJmsHeaderNames() throws Exception {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://nexary-activemq-codec?broker.persistent=false");
        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            Instant deadline = Instant.ofEpochMilli(Instant.now().plusSeconds(30).toEpochMilli());
            MessageEnvelope<String> envelope = new MessageEnvelope<>(
                    "app.error.log",
                    "app-42",
                    "payload",
                    Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "active-codec-1", "tenant-id", "demo"),
                    deadline,
                    null);

            Message jmsMessage = ActiveMqClassicMessageCodec.toMessage(
                    session,
                    new DefaultStringMessageSerializer(),
                    envelope);
            MessageEnvelope<String> decoded = ActiveMqClassicMessageCodec.toEnvelope(
                    "app.error.log",
                    new DefaultStringMessageSerializer(),
                    String.class,
                    jmsMessage);

            assertThat(jmsMessage.propertyExists(ActiveMqClassicMessageCodec.MESSAGE_ID_PROPERTY)).isTrue();
            assertThat(decoded.topic()).isEqualTo("app.error.log");
            assertThat(decoded.key()).isEqualTo("app-42");
            assertThat(decoded.messageId()).isEqualTo("active-codec-1");
            assertThat(decoded.payload()).isEqualTo("payload");
            assertThat(decoded.deadline()).isEqualTo(deadline);
            assertThat(decoded.headers()).containsEntry("tenant-id", "demo");
        }
    }

    @Test
    void skipsDuplicateMessagesAcrossListenerInvocations() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ActiveMqClassicMessageListenerAdapter<String> adapter = new ActiveMqClassicMessageListenerAdapter<>(
                executor(MessageDeadLetterPublisher.inMemory(), new ArrayList<>()),
                new DefaultStringMessageSerializer(),
                String.class,
                "app-error-log-consumer",
                envelope -> calls.incrementAndGet(),
                event -> {
                });

        MessageConsumeResult first = adapter.onMessage("app.error.log", message("active-duplicate-1", "payload"));
        MessageConsumeResult second = adapter.onMessage("app.error.log", message("active-duplicate-1", "payload"));

        assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DUPLICATE);
        assertThat(calls).hasValue(1);
    }

    @Test
    void returnsRetryThenDeadLetterWhenHandlerKeepsFailing() throws Exception {
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        ActiveMqClassicMessageListenerAdapter<String> adapter = new ActiveMqClassicMessageListenerAdapter<>(
                executor(deadLetters, events),
                new DefaultStringMessageSerializer(),
                String.class,
                "app-error-log-consumer",
                envelope -> {
                    throw new IllegalStateException("boom");
                },
                events::add);

        MessageConsumeResult first = adapter.onMessage("app.error.log", message("active-deadletter-1", "payload"));
        MessageConsumeResult second = adapter.onMessage("app.error.log", message("active-deadletter-1", "payload"));

        assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.RETRY);
        assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DEAD_LETTER);
        assertThat(deadLetters.records()).hasSize(1);
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("handler", "retry.schedule", "deadletter.publish", "consume");
        assertNoHighCardinalityTags(events);
    }

    @Test
    void governanceRejectionStopsBeforeOpeningActiveMqConnection() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        CountingConnectionFactory connectionFactory = new CountingConnectionFactory(
                "vm://nexary-activemq-governance?broker.persistent=false");
        ActiveMqClassicMessagePublisher publisher = new ActiveMqClassicMessagePublisher(
                connectionFactory,
                new DefaultStringMessageSerializer(),
                events::add,
                new RejectingGovernanceExecution("rate_limited"));
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "raw-user-topic-should-not-be-a-tag",
                "app-42",
                "payload",
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-governance-rejected"),
                null,
                null);

        MessagePublishResult result = publisher.publish(envelope).toCompletableFuture().join();

        assertThat(result.status()).isEqualTo(MessagePublishResult.PublishStatus.FAILED);
        assertThat(result.retrySignal()).isNotNull();
        assertThat(result.retrySignal().decision().name()).isEqualTo("STOP");
        assertThat(connectionFactory.calls).hasValue(0);
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("publish", "governance.retry.stopped");
        assertThat(events).anySatisfy(event -> assertThat(event.tags())
                .containsEntry("provider", "activemq_classic")
                .containsEntry("operation", "publish")
                .containsEntry("boundary", "rate_limited"));
        assertNoHighCardinalityTags(events);
    }

    private static MessageConsumeExecutor executor(
            MessageDeadLetterPublisher deadLetterPublisher,
            List<NexaryObservationEvent> events) {
        return new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetterPublisher,
                events::add);
    }

    private static BytesMessage message(String messageId, String payload) throws Exception {
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://nexary-activemq-listener?broker.persistent=false");
        try (Connection connection = connectionFactory.createConnection();
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
            MessageEnvelope<String> envelope = new MessageEnvelope<>(
                    "app.error.log",
                    "app-42",
                    payload,
                    Map.of(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
                    null,
                    null);
            return (BytesMessage) ActiveMqClassicMessageCodec.toMessage(
                    session,
                    new DefaultStringMessageSerializer(),
                    envelope);
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
            assertThat(event.tags().values())
                    .doesNotContain(
                            "active-deadletter-1",
                            "app.error.log",
                            "app-error-log-consumer",
                            "raw-user-topic-should-not-be-a-tag",
                            "message-governance-rejected",
                            "payload");
        });
    }

    private static final class CountingConnectionFactory extends ActiveMQConnectionFactory {
        private final AtomicInteger calls = new AtomicInteger();

        private CountingConnectionFactory(String brokerUrl) {
            super(brokerUrl);
        }

        @Override
        public Connection createConnection() throws JMSException {
            calls.incrementAndGet();
            return super.createConnection();
        }
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
