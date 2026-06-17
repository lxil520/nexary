package org.nexary.messaging.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.InMemoryMessageDeadLetterPublisher;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageRetryPolicy;
import org.nexary.messaging.MessageSubscription;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisMessageQueueIntegrationTest {
    @Test
    void publishesAndDeduplicatesWithRealRedisQueue() throws Exception {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisMessagingProperties properties = new RedisMessagingProperties();
            properties.setEnabled(true);
            properties.setPollTimeout(Duration.ofMillis(200));
            properties.setDeduplicationTtl(Duration.ofMinutes(5));
            MessageConsumeExecutor executor = new MessageConsumeExecutor(
                    java.util.Optional.of(new RedisMessageDeduplicationStore(fixture.stringRedisTemplate, properties)),
                    properties.getDeduplicationTtl(),
                    java.util.List.of(),
                    new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                    MessageDeadLetterPublisher.inMemory());
            RedisMessageQueue queue = new RedisMessageQueue(
                    fixture.stringRedisTemplate,
                    new DefaultStringMessageSerializer(),
                    executor,
                    properties);
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            String topic = "infra.redis." + UUID.randomUUID();
            MessageSubscription subscription = queue.subscribe(topic, "infra-group", String.class, envelope -> {
                calls.incrementAndGet();
                latch.countDown();
            });
            try {
                String messageId = "redis-message-" + UUID.randomUUID();
                MessageEnvelope<String> envelope = new MessageEnvelope<>(
                        topic,
                        "42",
                        "payload",
                        Map.of(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
                        null,
                        null);
                queue.publish(envelope).toCompletableFuture().join();
                queue.publish(envelope).toCompletableFuture().join();

                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(calls).hasValue(1);
            } finally {
                subscription.close();
                queue.close();
            }
        }
    }

    @Test
    void retriesAndDeadLettersWithRealRedisQueue() throws Exception {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisMessagingProperties properties = new RedisMessagingProperties();
            properties.setEnabled(true);
            properties.setPollTimeout(Duration.ofMillis(200));
            properties.setDeduplicationTtl(Duration.ofMinutes(5));
            properties.setRetryInitialDelay(Duration.ZERO);
            InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
            MessageConsumeExecutor executor = new MessageConsumeExecutor(
                    java.util.Optional.of(new RedisMessageDeduplicationStore(fixture.stringRedisTemplate, properties)),
                    properties.getDeduplicationTtl(),
                    java.util.List.of(),
                    new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                    deadLetters);
            RedisMessageQueue queue = new RedisMessageQueue(
                    fixture.stringRedisTemplate,
                    new DefaultStringMessageSerializer(),
                    executor,
                    properties);
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(2);
            String topic = "infra.redis.failure." + UUID.randomUUID();
            MessageSubscription subscription = queue.subscribe(topic, "infra-group", String.class, envelope -> {
                calls.incrementAndGet();
                latch.countDown();
                throw new IllegalStateException("boom");
            });
            try {
                String messageId = "redis-failure-message-" + UUID.randomUUID();
                MessageEnvelope<String> envelope = new MessageEnvelope<>(
                        topic,
                        "42",
                        "payload",
                        Map.of(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
                        null,
                        null);
                queue.publish(envelope).toCompletableFuture().join();

                assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(calls).hasValue(2);
                waitUntil(() -> deadLetters.records().size() == 1, Duration.ofSeconds(5));
                assertThat(deadLetters.records()).hasSize(1);

                queue.publish(envelope).toCompletableFuture().join();
                TimeUnit.MILLISECONDS.sleep(500);
                assertThat(calls).hasValue(2);
            } finally {
                subscription.close();
                queue.close();
            }
        }
    }

    @Test
    void recoversStaleProcessingMessageWithRealRedisQueue() throws Exception {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisMessagingProperties properties = new RedisMessagingProperties();
            properties.setEnabled(true);
            properties.setPollTimeout(Duration.ofMillis(200));
            properties.setVisibilityTimeout(Duration.ofMillis(100));
            properties.setProcessingRecoveryInterval(Duration.ofMillis(50));
            properties.setDeduplicationTtl(Duration.ofMinutes(5));
            MessageConsumeExecutor executor = new MessageConsumeExecutor(
                    java.util.Optional.of(new RedisMessageDeduplicationStore(fixture.stringRedisTemplate, properties)),
                    properties.getDeduplicationTtl(),
                    java.util.List.of(),
                    new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                    MessageDeadLetterPublisher.inMemory());
            RedisMessageQueue queue = new RedisMessageQueue(
                    fixture.stringRedisTemplate,
                    new DefaultStringMessageSerializer(),
                    executor,
                    properties);
            RedisStringTemplateQueueProcessingStore processingStore =
                    new RedisStringTemplateQueueProcessingStore(fixture.stringRedisTemplate, properties);
            CountDownLatch latch = new CountDownLatch(1);
            String topic = "infra.redis.stale." + UUID.randomUUID();
            String consumerGroup = "infra-group-" + UUID.randomUUID();
            try {
                String messageId = "redis-stale-message-" + UUID.randomUUID();
                MessageEnvelope<String> envelope = new MessageEnvelope<>(
                        topic,
                        "42",
                        "payload",
                        Map.of(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
                        null,
                        null);
                queue.publish(envelope).toCompletableFuture().join();

                String encoded = processingStore.moveReadyToProcessing(topic, consumerGroup, Duration.ofSeconds(1));
                assertThat(encoded).isNotNull();
                TimeUnit.MILLISECONDS.sleep(250);
                assertThat(processingStore.recoverStale(topic, consumerGroup)).isEqualTo(1);

                MessageSubscription subscription = queue.subscribe(topic, consumerGroup, String.class, message -> {
                    assertThat(message.messageId()).isEqualTo(messageId);
                    latch.countDown();
                });
                try {
                    assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
                } finally {
                    subscription.close();
                }
            } finally {
                queue.close();
            }
        }
    }

    private static final class RedisFixture implements AutoCloseable {
        private final LettuceConnectionFactory connectionFactory;
        private final StringRedisTemplate stringRedisTemplate;

        private RedisFixture() {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                    env("NEXARY_INFRA_REDIS_HOST", "127.0.0.1"),
                    Integer.parseInt(env("NEXARY_INFRA_REDIS_PORT", "16379")));
            this.connectionFactory = new LettuceConnectionFactory(configuration);
            this.connectionFactory.afterPropertiesSet();
            this.stringRedisTemplate = new StringRedisTemplate(connectionFactory);
            this.stringRedisTemplate.afterPropertiesSet();
        }

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }

    private static String env(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean infraTestsEnabled() {
        return Boolean.parseBoolean(env("NEXARY_RUN_INFRA_TESTS", "false"));
    }

    private static void waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(50);
        }
    }

    @FunctionalInterface
    private interface BooleanSupplier {
        boolean getAsBoolean();
    }
}
