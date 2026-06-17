package org.nexary.messaging.rocketmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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
import org.nexary.messaging.MessageRetryPolicy;

class RocketMqMessageListenerAdapterTest {
    @Test
    void skipsDuplicateMessagesAcrossListenerInvocations() {
        AtomicInteger calls = new AtomicInteger();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory());
        RocketMqMessageListenerAdapter<String> adapter = new RocketMqMessageListenerAdapter<>(
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
        RocketMqMessageListenerAdapter<String> adapter = new RocketMqMessageListenerAdapter<>(
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
    void emitsRocketMqConsumeStatusBoundaryEvents() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory(),
                events::add);
        AtomicInteger attempts = new AtomicInteger();
        RocketMqMessageListenerAdapter<String> adapter = new RocketMqMessageListenerAdapter<>(
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

        adapter.onMessage("raw-user-topic", "42", "payload".getBytes(), Map.of("nexary-message-id", "rocket-observe-1"));
        adapter.onMessage("raw-user-topic", "42", "payload".getBytes(), Map.of("nexary-message-id", "rocket-observe-1"));

        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("consume", "provider.consume_status", "retry.schedule");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("provider.consume_status");
            assertThat(event.tags()).containsEntry("provider", "rocketmq").containsEntry("boundary", "reconsume_later");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("provider.consume_status");
            assertThat(event.tags()).containsEntry("boundary", "consume_success");
        });
        assertNoHighCardinalityTags(events);
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
            assertThat(event.tags().values()).doesNotContain("rocket-observe-1", "raw-user-topic", "raw-user-group", "payload");
        });
    }
}
