package org.nexary.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernanceRejection;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.core.retry.RetryStopReason;

class MessageConsumeExecutorTest {
    @Test
    void dropsDuplicateMessageAfterSuccessfulConsume() {
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory());
        AtomicInteger calls = new AtomicInteger();
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "events",
                "42",
                "payload",
                java.util.Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-42"),
                null,
                null);

        MessageConsumeResult first = executor.consume(envelope, ignored -> calls.incrementAndGet());
        MessageConsumeResult second = executor.consume(envelope, ignored -> calls.incrementAndGet());

        assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DUPLICATE);
        assertThat(calls).hasValue(1);
    }

    @Test
    void releasesClaimWhenConsumerFails() {
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory());
        AtomicInteger calls = new AtomicInteger();
        MessageEnvelope<String> envelope = MessageEnvelope.of("events", "payload");

        MessageConsumeResult failed = executor.consume(envelope, ignored -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        MessageConsumeResult retry = executor.consume(envelope, ignored -> calls.incrementAndGet());

        assertThat(failed.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.RETRY);
        assertThat(retry.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
        assertThat(calls).hasValue(2);
    }

    @Test
    void writesOneDeadLetterRecordWhenRetryPolicyIsExhausted() {
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetters);
        AtomicInteger calls = new AtomicInteger();
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "events",
                "42",
                "payload",
                java.util.Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-dead-letter"),
                null,
                null);

        MessageConsumeResult first = executor.consume(envelope, "demo-group", ignored -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        MessageConsumeResult second = executor.consume(envelope, "demo-group", ignored -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        MessageConsumeResult third = executor.consume(envelope, "demo-group", ignored -> calls.incrementAndGet());

        assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.RETRY);
        assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DEAD_LETTER);
        assertThat(third.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DUPLICATE);
        assertThat(calls).hasValue(2);
        assertThat(deadLetters.records()).hasSize(1);
        assertThat(deadLetters.records().get(0).messageId()).isEqualTo("message-dead-letter");
        assertThat(deadLetters.records().get(0).consumerGroup()).isEqualTo("demo-group");
        assertThat(deadLetters.records().get(0).attempts()).isEqualTo(2);
    }

    @Test
    void doesNotCompleteDedupWhenDeadLetterPublishFails() {
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(1, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                record -> {
                    throw new IllegalStateException("dlq down");
                });
        AtomicInteger calls = new AtomicInteger();
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "events",
                "42",
                "payload",
                java.util.Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-dlq-down"),
                null,
                null);

        MessageConsumeResult failed = executor.consume(envelope, "demo-group", ignored -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        MessageConsumeResult retryAfterDlqRecovery = executor.consume(envelope, "demo-group", ignored -> calls.incrementAndGet());

        assertThat(failed.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.FAILED);
        assertThat(retryAfterDlqRecovery.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
        assertThat(calls).hasValue(2);
    }

    @Test
    void capsExponentialBackoff() {
        MessageRetryPolicy policy = new MessageRetryPolicy(
                5,
                Duration.ofMillis(100),
                MessageBackoffStrategy.EXPONENTIAL,
                3.0d,
                Duration.ofMillis(500));

        assertThat(policy.backoffFor(1)).isEqualTo(Duration.ofMillis(100));
        assertThat(policy.backoffFor(2)).isEqualTo(Duration.ofMillis(300));
        assertThat(policy.backoffFor(3)).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    void emitsBoundedObservationEventsForDedupRetryAndDeadLetter() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetters,
                events::add);
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "raw-user-topic-should-not-be-a-tag",
                "42",
                "payload",
                java.util.Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-observation-42"),
                null,
                null);

        MessageConsumeResult retry = executor.consume(envelope, "raw-user-group-should-not-be-a-tag", ignored -> {
            throw new IllegalStateException("boom with private details");
        });
        MessageConsumeResult terminal = executor.consume(envelope, "raw-user-group-should-not-be-a-tag", ignored -> {
            throw new IllegalStateException("boom with private details");
        });
        MessageConsumeResult duplicate = executor.consume(envelope, "raw-user-group-should-not-be-a-tag", ignored -> {
        });

        assertThat(retry.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.RETRY);
        assertThat(terminal.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DEAD_LETTER);
        assertThat(duplicate.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DUPLICATE);
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains(
                        "dedup.claim",
                        "handler",
                        "retry.schedule",
                        "deadletter.publish",
                        "governance.retry.stopped",
                        "governance.degraded");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("retry.schedule");
            assertThat(event.tags()).containsEntry("retry_attempt_bucket", "2_3");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("deadletter.publish");
            assertThat(event.tags()).containsEntry("terminal_status", "retry_exhausted");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("dedup.claim");
            assertThat(event.tags()).containsEntry("outcome", "duplicate");
        });
        assertNoHighCardinalityTags(events);
    }

    @Test
    void rejectsExpiredConsumeDeadlineBeforeCallingConsumer() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.empty(),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory(),
                events::add);
        AtomicInteger calls = new AtomicInteger();
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "raw-user-topic-should-not-be-a-tag",
                "42",
                "payload",
                java.util.Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-expired-consume"),
                Instant.now().minusMillis(1),
                null);

        MessageConsumeResult result = executor.consume(envelope, ignored -> calls.incrementAndGet());

        assertThat(result.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.FAILED);
        assertThat(result.retrySignal()).isNotNull();
        assertThat(result.retrySignal().decision().name()).isEqualTo("STOP");
        assertThat(result.retrySignal().stopReason()).isEqualTo(RetryStopReason.DEADLINE_EXPIRED);
        assertThat(calls).hasValue(0);
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("governance.deadline.exceeded", "governance.retry.stopped", "handler");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("handler");
            assertThat(event.tags())
                    .containsEntry("outcome", "failure")
                    .containsEntry("failure_category", "timeout")
                    .containsEntry("boundary", "deadline");
        });
        assertNoHighCardinalityTags(events);
    }

    @Test
    void emitsFailureObservationWhenDeadLetterPublishFails() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(1, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                record -> {
                    throw new IllegalStateException("dlq down with private details");
                },
                events::add);
        MessageEnvelope<String> envelope = new MessageEnvelope<>(
                "raw-user-topic-should-not-be-a-tag",
                "42",
                "payload",
                java.util.Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "message-dlq-failure"),
                null,
                null);

        MessageConsumeResult result = executor.consume(envelope, "raw-user-group-should-not-be-a-tag", ignored -> {
            throw new IllegalStateException("boom with private details");
        });

        assertThat(result.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.FAILED);
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("handler", "deadletter.publish", "governance.retry.stopped");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("deadletter.publish");
            assertThat(event.tags())
                    .containsEntry("outcome", "failure")
                    .containsEntry("failure_category", "application")
                    .containsEntry("terminal_status", "retry_exhausted");
        });
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .doesNotContain("governance.degraded");
        assertNoHighCardinalityTags(events);
    }

    @Test
    void noOpObservationPublisherPreservesConsumeBehavior() {
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                MessageDeadLetterPublisher.inMemory(),
                NexaryObservationPublisher.noop());

        MessageConsumeResult result = executor.consume(MessageEnvelope.of("events", "payload"), ignored -> {
        });

        assertThat(result.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
    }

    @Test
    void slowHandlerCallCanOpenCircuitThroughGovernanceExecution() {
        CircuitOpeningGovernanceExecution governanceExecution =
                new CircuitOpeningGovernanceExecution(Duration.ofMillis(1));
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.empty(),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(3, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetters,
                NexaryObservationPublisher.noop(),
                governanceExecution,
                "core");
        AtomicInteger calls = new AtomicInteger();
        MessageEnvelope<String> envelope = MessageEnvelope.of("events", "payload");

        MessageConsumeResult first = executor.consume(envelope, ignored -> {
            calls.incrementAndGet();
            Thread.sleep(20);
        });
        MessageConsumeResult second = executor.consume(envelope, ignored -> calls.incrementAndGet());

        assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
        assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.FAILED);
        assertThat(second.retrySignal().decision().name()).isEqualTo("STOP");
        assertThat(second.retrySignal().stopReason()).isEqualTo(RetryStopReason.CIRCUIT_OPEN);
        assertThat(calls).hasValue(1);
        assertThat(governanceExecution.executions).isEqualTo(1);
        assertThat(deadLetters.records()).isEmpty();
    }

    @Test
    void dropsUnsafeObservationTags() {
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();

        MessageObservationSupport.publish(
                events::add,
                "publish",
                "kafka",
                "success",
                java.util.Map.of(
                        "message_id", "message-42",
                        "payload", "secret",
                        "topic", "raw-topic",
                        "consumer_group", "raw-group",
                        "retry_attempt_bucket", "1"),
                new IllegalStateException("exception message must not be a tag"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).tags()).containsEntry("retry_attempt_bucket", "1");
        assertNoHighCardinalityTags(events);
    }

    private static void assertNoHighCardinalityTags(List<NexaryObservationEvent> events) {
        events.forEach(event -> {
            assertThat(event.tags()).doesNotContainKeys(
                    "message_id",
                    "payload",
                    "topic",
                    "consumer_group",
                    "exception",
                    "exception_message",
                    "stack_trace");
            assertThat(event.tags().values()).doesNotContain(
                    "message-observation-42",
                    "payload",
                    "raw-user-topic-should-not-be-a-tag",
                    "raw-user-group-should-not-be-a-tag",
                    "boom with private details");
        });
    }

    private static final class CircuitOpeningGovernanceExecution implements GovernanceExecution {
        private final Duration slowThreshold;
        private boolean open;
        private int executions;

        private CircuitOpeningGovernanceExecution(Duration slowThreshold) {
            this.slowThreshold = slowThreshold;
        }

        @Override
        public Object execute(GovernanceContext context, Callable<?> action) throws Exception {
            if (open) {
                throw new CircuitOpenException();
            }
            executions++;
            long started = System.nanoTime();
            try {
                return GovernanceContext.callWithContext(context, action);
            } catch (Exception ex) {
                open = true;
                throw ex;
            } finally {
                if (Duration.ofNanos(System.nanoTime() - started).compareTo(slowThreshold) > 0) {
                    open = true;
                }
            }
        }
    }

    private static final class CircuitOpenException extends RuntimeException implements GovernanceRejection {
        @Override
        public String governanceRejectionReason() {
            return "circuit_open";
        }
    }
}
