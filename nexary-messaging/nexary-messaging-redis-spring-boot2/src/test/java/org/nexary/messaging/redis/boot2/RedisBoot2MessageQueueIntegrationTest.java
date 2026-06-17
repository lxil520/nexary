package org.nexary.messaging.redis.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
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

class RedisBoot2MessageQueueIntegrationTest {
    @Test
    void publishesAndDeduplicatesWithRealRedisQueue() throws Exception {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisBoot2MessagingProperties properties = new RedisBoot2MessagingProperties();
            properties.setEnabled(true);
            properties.setPollTimeout(Duration.ofMillis(200));
            properties.setDeduplicationTtl(Duration.ofMinutes(5));
            MessageConsumeExecutor executor = new MessageConsumeExecutor(
                    java.util.Optional.of(new RedisBoot2MessageDeduplicationStore(fixture.stringRedisTemplate, properties)),
                    properties.getDeduplicationTtl(),
                    Collections.emptyList(),
                    new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                    MessageDeadLetterPublisher.inMemory());
            RedisBoot2MessageQueue queue = new RedisBoot2MessageQueue(
                    fixture.stringRedisTemplate,
                    new DefaultStringMessageSerializer(),
                    executor,
                    properties);
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(1);
            String topic = "infra.redis.boot2." + UUID.randomUUID();
            MessageSubscription subscription = queue.subscribe(topic, "infra-group", String.class, envelope -> {
                calls.incrementAndGet();
                latch.countDown();
            });
            try {
                String messageId = "redis-boot2-message-" + UUID.randomUUID();
                MessageEnvelope<String> envelope = new MessageEnvelope<>(
                        topic,
                        "42",
                        "payload",
                        Collections.singletonMap(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
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
            RedisBoot2MessagingProperties properties = new RedisBoot2MessagingProperties();
            properties.setEnabled(true);
            properties.setPollTimeout(Duration.ofMillis(200));
            properties.setDeduplicationTtl(Duration.ofMinutes(5));
            properties.setRetryInitialDelay(Duration.ZERO);
            InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
            MessageConsumeExecutor executor = new MessageConsumeExecutor(
                    java.util.Optional.of(new RedisBoot2MessageDeduplicationStore(fixture.stringRedisTemplate, properties)),
                    properties.getDeduplicationTtl(),
                    Collections.emptyList(),
                    new MessageRetryPolicy(2, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                    deadLetters);
            RedisBoot2MessageQueue queue = new RedisBoot2MessageQueue(
                    fixture.stringRedisTemplate,
                    new DefaultStringMessageSerializer(),
                    executor,
                    properties);
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch latch = new CountDownLatch(2);
            String topic = "infra.redis.boot2.failure." + UUID.randomUUID();
            MessageSubscription subscription = queue.subscribe(topic, "infra-group", String.class, envelope -> {
                calls.incrementAndGet();
                latch.countDown();
                throw new IllegalStateException("boom");
            });
            try {
                String messageId = "redis-boot2-failure-message-" + UUID.randomUUID();
                MessageEnvelope<String> envelope = new MessageEnvelope<>(
                        topic,
                        "42",
                        "payload",
                        Collections.singletonMap(MessageEnvelope.MESSAGE_ID_HEADER, messageId),
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
        if (property != null && !property.trim().isEmpty()) {
            return property;
        }
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value;
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
