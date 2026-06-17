package org.nexary.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.InMemoryMessageDeadLetterPublisher;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageRetryPolicy;

class RedisMessageQueueTest {
    @Test
    void movesReadyMessageToProcessingAndAcksOnlyAfterSuccess() throws Exception {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        RedisMessageQueue queue = newQueue(store, MessageDeadLetterPublisher.inMemory());
        CountDownLatch latch = new CountDownLatch(1);
        String topic = "unit.redis.ack";
        MessageEnvelope<String> envelope = envelope(topic, "redis-ack-1");

        queue.publish(envelope).toCompletableFuture().join();
        try (var subscription = queue.subscribe(topic, "unit-group", String.class, message -> latch.countDown())) {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> store.processingSize(topic, "unit-group") == 0, Duration.ofSeconds(5));
            assertThat(store.readySize(topic)).isZero();
            assertThat(store.processingSize(topic, "unit-group")).isZero();
            assertThat(store.ackedIds()).containsExactly("redis-ack-1");
        } finally {
            queue.close();
        }
    }

    @Test
    void retryRequeuesTheSameMessageIdBeforeSuccessAck() throws Exception {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        RedisMessageQueue queue = newQueue(store, MessageDeadLetterPublisher.inMemory());
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        List<String> seenIds = new CopyOnWriteArrayList<>();
        String topic = "unit.redis.retry";

        queue.publish(envelope(topic, "redis-retry-1")).toCompletableFuture().join();
        try (var subscription = queue.subscribe(topic, "unit-group", String.class, message -> {
            seenIds.add(message.messageId());
            latch.countDown();
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt fails");
            }
        })) {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> store.requeuedIds().contains("redis-retry-1"), Duration.ofSeconds(5));
            waitUntil(() -> store.ackedIds().contains("redis-retry-1"), Duration.ofSeconds(5));
            assertThat(store.requeuedIds()).containsExactly("redis-retry-1");
            assertThat(store.ackedIds()).containsExactly("redis-retry-1");
            assertThat(seenIds).containsExactly("redis-retry-1", "redis-retry-1");
        } finally {
            queue.close();
        }
    }

    @Test
    void retryExhaustionAcksProcessingAndPublishesOneDeadLetterRecord() throws Exception {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        RedisMessageQueue queue = newQueue(store, deadLetters);
        CountDownLatch latch = new CountDownLatch(2);
        String topic = "unit.redis.deadletter";

        queue.publish(envelope(topic, "redis-deadletter-1")).toCompletableFuture().join();
        try (var subscription = queue.subscribe(topic, "unit-group", String.class, message -> {
            latch.countDown();
            throw new IllegalStateException("always fails");
        })) {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> deadLetters.records().size() == 1, Duration.ofSeconds(5));
            waitUntil(() -> store.ackedIds().contains("redis-deadletter-1"), Duration.ofSeconds(5));
            assertThat(deadLetters.records()).hasSize(1);
            assertThat(deadLetters.records().get(0).messageId()).isEqualTo("redis-deadletter-1");
            assertThat(store.ackedIds()).containsExactly("redis-deadletter-1");
            assertThat(store.processingSize(topic, "unit-group")).isZero();
        } finally {
            queue.close();
        }
    }

    @Test
    void staleProcessingRecoveryMovesExpiredLeaseBackToReady() {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        String encoded = "redis-stale-1|key|payload|headers";
        store.putProcessing("unit.redis.stale", "unit-group", encoded, false);

        int recovered = store.recoverStale("unit.redis.stale", "unit-group");

        assertThat(recovered).isOne();
        assertThat(store.readySize("unit.redis.stale")).isOne();
        assertThat(store.processingSize("unit.redis.stale", "unit-group")).isZero();
    }

    @Test
    void emitsRedisProviderAckRequeueAndRecoveryEvents() throws Exception {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        RedisMessageQueue queue = newQueue(store, MessageDeadLetterPublisher.inMemory(), events);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        String topic = "unit.redis.observation";

        queue.publish(envelope(topic, "redis-observe-1")).toCompletableFuture().join();
        try (var subscription = queue.subscribe(topic, "unit-group", String.class, message -> {
            latch.countDown();
            if (calls.incrementAndGet() == 1) {
                throw new IllegalStateException("first attempt fails");
            }
        })) {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> events.stream().anyMatch(event -> "provider.ack".equals(event.operation())), Duration.ofSeconds(5));
        } finally {
            queue.close();
        }

        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("publish", "consume", "provider.requeue", "provider.ack", "provider.recovery");
        assertThat(events).anySatisfy(event -> {
            assertThat(event.operation()).isEqualTo("provider.requeue");
            assertThat(event.tags()).containsEntry("provider", "redis").containsEntry("outcome", "success");
        });
        assertNoHighCardinalityTags(events);
    }

    private static RedisMessageQueue newQueue(
            RedisQueueProcessingStore store,
            MessageDeadLetterPublisher deadLetterPublisher) {
        RedisMessagingProperties properties = new RedisMessagingProperties();
        properties.setPollTimeout(Duration.ofMillis(20));
        properties.setProcessingRecoveryInterval(Duration.ofMillis(20));
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofSeconds(30),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetterPublisher);
        return new RedisMessageQueue(store, new DefaultStringMessageSerializer(), executor, properties);
    }

    private static RedisMessageQueue newQueue(
            RedisQueueProcessingStore store,
            MessageDeadLetterPublisher deadLetterPublisher,
            List<NexaryObservationEvent> events) {
        RedisMessagingProperties properties = new RedisMessagingProperties();
        properties.setPollTimeout(Duration.ofMillis(20));
        properties.setProcessingRecoveryInterval(Duration.ofMillis(20));
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofSeconds(30),
                List.of(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetterPublisher,
                events::add);
        return new RedisMessageQueue(store, new DefaultStringMessageSerializer(), executor, properties, events::add);
    }

    private static MessageEnvelope<String> envelope(String topic, String messageId) {
        return new MessageEnvelope<>(
                topic,
                "key",
                "payload",
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
                null,
                null);
    }

    private static void waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }

    private static void assertNoHighCardinalityTags(List<NexaryObservationEvent> events) {
        events.forEach(event -> assertThat(event.tags()).doesNotContainKeys(
                "message_id",
                "payload",
                "topic",
                "consumer_group",
                "exception_message",
                "stack_trace"));
    }

    private static final class InMemoryProcessingStore implements RedisQueueProcessingStore {
        private final Map<String, ArrayDeque<String>> ready = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, ArrayDeque<String>> processing = new java.util.concurrent.ConcurrentHashMap<>();
        private final Map<String, Boolean> leases = new java.util.concurrent.ConcurrentHashMap<>();
        private final List<String> ackedIds = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final List<String> requeuedIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public synchronized boolean enqueueReady(String topic, String encoded) {
            ready.computeIfAbsent(topic, ignored -> new ArrayDeque<>()).addFirst(encoded);
            return true;
        }

        @Override
        public synchronized String moveReadyToProcessing(String topic, String consumerGroup, Duration pollTimeout) {
            ArrayDeque<String> queue = ready.computeIfAbsent(topic, ignored -> new ArrayDeque<>());
            String encoded = queue.pollLast();
            if (encoded == null) {
                sleep(pollTimeout);
                return null;
            }
            processing.computeIfAbsent(processingKey(topic, consumerGroup), ignored -> new ArrayDeque<>()).addFirst(encoded);
            leases.put(leaseKey(topic, consumerGroup, encoded), true);
            return encoded;
        }

        @Override
        public synchronized void ack(String topic, String consumerGroup, String encoded) {
            removeProcessing(topic, consumerGroup, encoded);
            leases.remove(leaseKey(topic, consumerGroup, encoded));
            ackedIds.add(messageId(encoded));
        }

        @Override
        public synchronized void extendLease(String topic, String consumerGroup, String encoded, Duration extension) {
            leases.put(leaseKey(topic, consumerGroup, encoded), true);
        }

        @Override
        public synchronized void requeue(String topic, String consumerGroup, String encoded) {
            ready.computeIfAbsent(topic, ignored -> new ArrayDeque<>()).addFirst(encoded);
            removeProcessing(topic, consumerGroup, encoded);
            leases.remove(leaseKey(topic, consumerGroup, encoded));
            requeuedIds.add(messageId(encoded));
        }

        @Override
        public synchronized int recoverStale(String topic, String consumerGroup) {
            ArrayDeque<String> queue = processing.computeIfAbsent(processingKey(topic, consumerGroup), ignored -> new ArrayDeque<>());
            List<String> stale = queue.stream()
                    .filter(encoded -> !leases.containsKey(leaseKey(topic, consumerGroup, encoded)))
                    .toList();
            stale.forEach(encoded -> {
                ready.computeIfAbsent(topic, ignored -> new ArrayDeque<>()).addFirst(encoded);
                queue.remove(encoded);
            });
            return stale.size();
        }

        private synchronized void putProcessing(String topic, String consumerGroup, String encoded, boolean leased) {
            processing.computeIfAbsent(processingKey(topic, consumerGroup), ignored -> new ArrayDeque<>()).addFirst(encoded);
            if (leased) {
                leases.put(leaseKey(topic, consumerGroup, encoded), true);
            }
        }

        private synchronized int readySize(String topic) {
            return ready.computeIfAbsent(topic, ignored -> new ArrayDeque<>()).size();
        }

        private synchronized int processingSize(String topic, String consumerGroup) {
            return processing.computeIfAbsent(processingKey(topic, consumerGroup), ignored -> new ArrayDeque<>()).size();
        }

        private List<String> ackedIds() {
            return ackedIds;
        }

        private List<String> requeuedIds() {
            return requeuedIds;
        }

        private void removeProcessing(String topic, String consumerGroup, String encoded) {
            processing.computeIfAbsent(processingKey(topic, consumerGroup), ignored -> new ArrayDeque<>()).remove(encoded);
        }

        private String processingKey(String topic, String consumerGroup) {
            return topic + ":" + consumerGroup;
        }

        private String leaseKey(String topic, String consumerGroup, String encoded) {
            return processingKey(topic, consumerGroup) + ":" + messageId(encoded);
        }

        private String messageId(String encoded) {
            return encoded.substring(0, encoded.indexOf('|'));
        }

        private void sleep(Duration duration) {
            try {
                TimeUnit.MILLISECONDS.sleep(Math.min(duration.toMillis(), 20));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
