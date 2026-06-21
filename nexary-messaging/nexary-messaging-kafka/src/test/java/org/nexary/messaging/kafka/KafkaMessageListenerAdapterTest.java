package org.nexary.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.InMemoryMessageDeadLetterPublisher;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageRetryPolicy;

class KafkaMessageListenerAdapterTest {
    @Test
    void skipsDuplicateMessagesAcrossListenerInvocations() {
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory());
        KafkaMessageListenerAdapter<String> adapter = new KafkaMessageListenerAdapter<>(
                executor,
                new DefaultStringMessageSerializer(),
                String.class,
                envelope -> calls.incrementAndGet());
        MessageConsumeResult first = adapter.onMessage(
                "events",
                "42",
                "payload".getBytes(),
                Map.of("nexary-message-id", "message-42"));
        MessageConsumeResult second = adapter.onMessage(
                "events",
                "42",
                "payload".getBytes(),
                Map.of("nexary-message-id", "message-42"));

        assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DUPLICATE);
        assertThat(calls).hasValue(1);
    }

    @Test
    void returnsRetryThenDeadLetterWhenHandlerKeepsFailing() {
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetters);
        KafkaMessageListenerAdapter<String> adapter = new KafkaMessageListenerAdapter<>(
                executor,
                new DefaultStringMessageSerializer(),
                String.class,
                envelope -> {
                    throw new IllegalStateException("boom");
                });

        MessageConsumeResult first = adapter.onMessage(
                "events",
                "42",
                "payload".getBytes(),
                Map.of("nexary-message-id", "message-failure-42"));
        MessageConsumeResult second = adapter.onMessage(
                "events",
                "42",
                "payload".getBytes(),
                Map.of("nexary-message-id", "message-failure-42"));

        assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.RETRY);
        assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DEAD_LETTER);
        assertThat(deadLetters.records()).hasSize(1);
    }

    @Test
    void emitsKafkaCommitAndSeekBoundaryEvents() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory(),
                events::add);
        AtomicInteger attempts = new AtomicInteger();
        KafkaMessageListenerAdapter<String> adapter = new KafkaMessageListenerAdapter<>(
                executor,
                new DefaultStringMessageSerializer(),
                String.class,
                "raw-user-group",
                envelope -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("boom");
                    }
                },
                events::add);

        adapter.onMessage("raw-user-topic", "42", "payload".getBytes(), Map.of("nexary-message-id", "kafka-observe-1"));
        adapter.onMessage("raw-user-topic", "42", "payload".getBytes(), Map.of("nexary-message-id", "kafka-observe-1"));

        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("consume", "provider.seek", "provider.commit", "retry.schedule");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("provider.seek");
            assertThat(event.tags()).containsEntry("provider", "kafka").containsEntry("boundary", "seek_current");
        });
        assertNoHighCardinalityTags(events);
    }

    @Test
    void rejectsExpiredDeadlineHeaderBeforeCallingHandler() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.empty(),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory(),
                events::add);
        KafkaMessageListenerAdapter<String> adapter = new KafkaMessageListenerAdapter<>(
                executor,
                new DefaultStringMessageSerializer(),
                String.class,
                "raw-user-group",
                envelope -> calls.incrementAndGet(),
                events::add);

        MessageConsumeResult result = adapter.onMessage(
                "raw-user-topic",
                "42",
                "payload".getBytes(),
                Map.of(
                        "nexary-message-id", "kafka-expired-1",
                        MessageEnvelope.DEADLINE_HEADER, Long.toString(Instant.now().minusMillis(1).toEpochMilli())));

        assertThat(result.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.FAILED);
        assertThat(result.retrySignal()).isNotNull();
        assertThat(result.retrySignal().decision().name()).isEqualTo("STOP");
        assertThat(calls).hasValue(0);
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("governance.deadline.exceeded", "governance.retry.stopped", "consume");
        assertNoHighCardinalityTags(events);
    }

    private final AtomicInteger calls = new AtomicInteger();

    private static void assertNoHighCardinalityTags(List<NexaryObservationEvent> events) {
        events.forEach(event -> {
            assertThat(event.tags()).doesNotContainKeys(
                    "message_id",
                    "payload",
                    "topic",
                    "consumer_group",
                    "exception_message",
                    "stack_trace");
            assertThat(event.tags().values()).doesNotContain("kafka-observe-1", "raw-user-topic", "raw-user-group", "payload");
        });
    }
}
