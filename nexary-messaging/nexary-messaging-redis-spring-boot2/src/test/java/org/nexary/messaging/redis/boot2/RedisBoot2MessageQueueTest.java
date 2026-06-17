package org.nexary.messaging.redis.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.InMemoryMessageDeadLetterPublisher;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageRetryPolicy;

class RedisBoot2MessageQueueTest {
    @Test
    void retryRequeuesSameMessageIdThenAcksOnSuccess() throws Exception {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        RedisBoot2MessageQueue queue = newQueue(store, MessageDeadLetterPublisher.inMemory());
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger calls = new AtomicInteger();
        List<String> seenIds = new CopyOnWriteArrayList<>();
        String topic = "unit.redis.boot2.retry";

        queue.publish(envelope(topic, "redis-boot2-retry-1")).toCompletableFuture().join();
        try (org.nexary.messaging.MessageSubscription subscription =
                     queue.subscribe(topic, "unit-group", String.class, message -> {
                         seenIds.add(message.messageId());
                         latch.countDown();
                         if (calls.incrementAndGet() == 1) {
                             throw new IllegalStateException("first attempt fails");
                         }
                     })) {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> store.requeuedIds().contains("redis-boot2-retry-1"), Duration.ofSeconds(5));
            waitUntil(() -> store.ackedIds().contains("redis-boot2-retry-1"), Duration.ofSeconds(5));
            assertThat(store.requeuedIds()).containsExactly("redis-boot2-retry-1");
            assertThat(store.ackedIds()).containsExactly("redis-boot2-retry-1");
            assertThat(seenIds).containsExactly("redis-boot2-retry-1", "redis-boot2-retry-1");
        } finally {
            queue.close();
        }
    }

    @Test
    void retryExhaustionAcksProcessingAndPublishesOneDeadLetter() throws Exception {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        RedisBoot2MessageQueue queue = newQueue(store, deadLetters);
        CountDownLatch latch = new CountDownLatch(2);
        String topic = "unit.redis.boot2.deadletter";

        queue.publish(envelope(topic, "redis-boot2-deadletter-1")).toCompletableFuture().join();
        try (org.nexary.messaging.MessageSubscription subscription =
                     queue.subscribe(topic, "unit-group", String.class, message -> {
                         latch.countDown();
                         throw new IllegalStateException("always fails");
                     })) {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            waitUntil(() -> deadLetters.records().size() == 1, Duration.ofSeconds(5));
            waitUntil(() -> store.ackedIds().contains("redis-boot2-deadletter-1"), Duration.ofSeconds(5));
            assertThat(deadLetters.records()).hasSize(1);
            assertThat(deadLetters.records().get(0).messageId()).isEqualTo("redis-boot2-deadletter-1");
            assertThat(store.ackedIds()).containsExactly("redis-boot2-deadletter-1");
            assertThat(store.processingSize(topic, "unit-group")).isZero();
        } finally {
            queue.close();
        }
    }

    @Test
    void staleProcessingRecoveryMovesExpiredLeaseBackToReady() {
        InMemoryProcessingStore store = new InMemoryProcessingStore();
        String encoded = "redis-boot2-stale-1|key|payload|headers";
        store.putProcessing("unit.redis.boot2.stale", "unit-group", encoded, false);

        int recovered = store.recoverStale("unit.redis.boot2.stale", "unit-group");

        assertThat(recovered).isOne();
        assertThat(store.readySize("unit.redis.boot2.stale")).isOne();
        assertThat(store.processingSize("unit.redis.boot2.stale", "unit-group")).isZero();
    }

    private static RedisBoot2MessageQueue newQueue(
            RedisBoot2QueueProcessingStore store,
            MessageDeadLetterPublisher deadLetterPublisher) {
        RedisBoot2MessagingProperties properties = new RedisBoot2MessagingProperties();
        properties.setPollTimeout(Duration.ofMillis(20));
        properties.setProcessingRecoveryInterval(Duration.ofMillis(20));
        MessageConsumeExecutor executor = new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofSeconds(30),
                Collections.emptyList(),
                new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetterPublisher);
        return new RedisBoot2MessageQueue(store, new DefaultStringMessageSerializer(), executor, properties);
    }

    private static MessageEnvelope<String> envelope(String topic, String messageId) {
        return new MessageEnvelope<>(
                topic,
                "key",
                "payload",
                Collections.singletonMap(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
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

    private static final class InMemoryProcessingStore implements RedisBoot2QueueProcessingStore {
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
            leases.put(leaseKey(topic, consumerGroup, encoded), Boolean.TRUE);
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
            leases.put(leaseKey(topic, consumerGroup, encoded), Boolean.TRUE);
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
            ArrayDeque<String> queue =
                    processing.computeIfAbsent(processingKey(topic, consumerGroup), ignored -> new ArrayDeque<>());
            List<String> stale = new java.util.ArrayList<>();
            for (String encoded : queue) {
                if (!Boolean.TRUE.equals(leases.get(leaseKey(topic, consumerGroup, encoded)))) {
                    stale.add(encoded);
                }
            }
            for (String encoded : stale) {
                queue.remove(encoded);
                ready.computeIfAbsent(topic, ignored -> new ArrayDeque<>()).addFirst(encoded);
            }
            return stale.size();
        }

        private void putProcessing(String topic, String consumerGroup, String encoded, boolean leaseActive) {
            processing.computeIfAbsent(processingKey(topic, consumerGroup), ignored -> new ArrayDeque<>()).addFirst(encoded);
            leases.put(leaseKey(topic, consumerGroup, encoded), leaseActive);
        }

        private int readySize(String topic) {
            return ready.computeIfAbsent(topic, ignored -> new ArrayDeque<>()).size();
        }

        private int processingSize(String topic, String consumerGroup) {
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
            int separator = encoded.indexOf('|');
            return separator < 0 ? encoded : encoded.substring(0, separator);
        }

        private void sleep(Duration duration) {
            try {
                TimeUnit.MILLISECONDS.sleep(Math.max(1L, duration.toMillis()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
