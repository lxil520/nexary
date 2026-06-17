package org.nexary.messaging.disruptor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.InMemoryMessageDeadLetterPublisher;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageRetryPolicy;

class DisruptorMessageBusTest {
    @Test
    void dispatchesMessageToSubscriber() throws Exception {
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory());
        try (DisruptorMessageBus bus = new DisruptorMessageBus(8, executor)) {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<String> consumed = new AtomicReference<>();
            bus.subscribe("events", "demo", String.class, envelope -> {
                consumed.set(envelope.payload());
                latch.countDown();
            });

            bus.publish(MessageEnvelope.of("events", "payload"));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(consumed).hasValue("payload");
        }
    }

    @Test
    void retriesFailedMessageThenDeadLettersThroughSharedExecutor() throws Exception {
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetters);
        try (DisruptorMessageBus bus = new DisruptorMessageBus(8, executor)) {
            CountDownLatch latch = new CountDownLatch(2);
            AtomicInteger calls = new AtomicInteger();
            bus.subscribe("events", "demo", String.class, envelope -> {
                calls.incrementAndGet();
                latch.countDown();
                throw new IllegalStateException("boom");
            });

            bus.publish(MessageEnvelope.of("events", "payload"));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(2);
            assertThat(deadLetters.records()).hasSize(1);
            assertThat(deadLetters.records().get(0).consumerGroup()).isEqualTo("demo");
        }
    }

    @Test
    void emitsDisruptorPublishDispatchAndConsumeEvents() throws Exception {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory(),
                events::add);
        try (DisruptorMessageBus bus = new DisruptorMessageBus(8, executor, events::add)) {
            CountDownLatch latch = new CountDownLatch(1);
            bus.subscribe("raw-user-topic", "raw-user-group", String.class, envelope -> latch.countDown());

            bus.publish(MessageEnvelope.of("raw-user-topic", "payload"));

            assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(events).extracting(NexaryObservationEvent::operation)
                    .contains("publish", "dispatch", "consume", "handler");
            assertThat(events).anySatisfy(event -> {
                assertThat(event.operation()).isEqualTo("publish");
                assertThat(event.tags()).containsEntry("provider", "disruptor").containsEntry("outcome", "success");
            });
            assertNoHighCardinalityTags(events);
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
            assertThat(event.tags().values()).doesNotContain("raw-user-topic", "raw-user-group", "payload");
        });
    }
}
