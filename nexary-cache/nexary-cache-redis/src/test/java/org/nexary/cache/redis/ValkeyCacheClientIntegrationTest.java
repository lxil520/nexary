package org.nexary.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.cache.counter.CacheCounterKey;
import org.nexary.cache.counter.CacheCounterMutation;
import org.nexary.cache.invalidation.CacheInvalidationPublisher;
import org.nexary.cache.redis.invalidation.RedisCacheInvalidationPublisher;
import org.nexary.cache.redis.invalidation.RedisCacheInvalidationSubscriber;
import org.nexary.cache.tiered.LocalCacheClient;
import org.nexary.cache.tiered.TieredCacheClient;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class ValkeyCacheClientIntegrationTest {
    @Test
    void redisProtocolCacheOperationsWorkAgainstRealValkey() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (ValkeyFixture fixture = new ValkeyFixture()) {
            RedisCacheProperties properties = new RedisCacheProperties();
            properties.setProviderName("valkey");
            properties.setDefaultTtl(Duration.ofSeconds(5));
            properties.setLockRetryInterval(Duration.ofMillis(20));
            RecordingObservationPublisher observationPublisher = new RecordingObservationPublisher();
            RedisCacheClient client =
                    new RedisCacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, properties, observationPublisher);
            CacheKey valueKey = CacheKey.of("valkey-cache", UUID.randomUUID().toString());
            CacheKey otherKey = CacheKey.of("valkey-cache", UUID.randomUUID().toString());
            CacheKey lockKey = CacheKey.of("valkey-lock", UUID.randomUUID().toString());

            client.put(valueKey, "Alice", Duration.ofSeconds(5));
            String loaded = client.cacheAside(valueKey, String.class, Duration.ofSeconds(5), () -> "fallback");
            client.putAll(Map.of(otherKey, "Bob"), Duration.ofSeconds(5));
            Long ttl = fixture.stringRedisTemplate.getExpire(valueKey.qualified(), TimeUnit.MILLISECONDS);
            LockHandle lock = client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(2)).orElseThrow();
            long firstToken = lock.fencingToken().orElseThrow();

            assertThat(loaded).isEqualTo("Alice");
            assertThat(client.get(valueKey, String.class)).contains("Alice");
            assertThat(client.getAll(List.of(valueKey, otherKey))).containsEntry(valueKey, "Alice").containsEntry(otherKey, "Bob");
            assertThat(ttl).isNotNull().isBetween(1L, 5000L);
            assertThat(client.expire(valueKey, Duration.ofSeconds(10))).isTrue();
            assertThat(lock.renew(Duration.ofSeconds(3))).isTrue();
            lock.close();
            LockHandle secondLock = client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(2)).orElseThrow();
            assertThat(secondLock.fencingToken()).isPresent();
            assertThat(secondLock.fencingToken().orElseThrow()).isGreaterThan(firstToken);
            secondLock.close();
            assertThat(client.delete(valueKey)).isTrue();

            assertThat(observationPublisher.events)
                    .allSatisfy(event -> assertThat(event.tags()).containsEntry("provider", "valkey"));
            assertThat(observationPublisher.events)
                    .extracting(event -> event.tags().get("operation"))
                    .contains("cache.put", "cache.get", "cache.batch_get", "cache.batch_put", "cache.expire",
                            "cache.lock_acquire", "cache.lock_renew", "cache.lock_release", "cache.delete");
            assertBoundedTags(observationPublisher.events);
        }
    }

    @Test
    void valkeyAtomicCounterUsesTtlOnCreateOnly() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (ValkeyFixture fixture = new ValkeyFixture()) {
            RecordingObservationPublisher observationPublisher = new RecordingObservationPublisher();
            RedisCacheCounterClient counterClient =
                    new RedisCacheCounterClient(fixture.stringRedisTemplate, observationPublisher, "valkey");
            CacheCounterKey key = CacheCounterKey.of("valkey-counter", UUID.randomUUID().toString());

            CacheCounterMutation first = counterClient.increment(key, 5, Duration.ofSeconds(3));
            Long firstTtl = fixture.stringRedisTemplate.getExpire(key.qualified(), TimeUnit.MILLISECONDS);
            CacheCounterMutation second = counterClient.increment(key, 2, Duration.ofSeconds(30));
            Long secondTtl = fixture.stringRedisTemplate.getExpire(key.qualified(), TimeUnit.MILLISECONDS);
            CacheCounterMutation third = counterClient.decrement(key, 3, Duration.ofSeconds(30));

            assertThat(first.value()).isEqualTo(5);
            assertThat(first.created()).isTrue();
            assertThat(first.ttlApplied()).isTrue();
            assertThat(firstTtl).isNotNull().isBetween(1L, 3000L);
            assertThat(second.value()).isEqualTo(7);
            assertThat(second.created()).isFalse();
            assertThat(second.ttlApplied()).isFalse();
            assertThat(secondTtl).isNotNull().isBetween(1L, 3000L);
            assertThat(third.value()).isEqualTo(4);
            assertThat(counterClient.current(key)).hasValue(4);
            assertThat(counterClient.clear(key)).isTrue();
            assertThat(counterClient.current(key)).isEmpty();
            assertThat(observationPublisher.events)
                    .allSatisfy(event -> assertThat(event.tags()).containsEntry("provider", "valkey"));
            assertBoundedTags(observationPublisher.events);
        }
    }

    @Test
    void valkeyPubSubInvalidationRefreshesTieredLocalCache() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (ValkeyFixture fixture = new ValkeyFixture()) {
            RedisCacheProperties properties = new RedisCacheProperties();
            properties.setProviderName("valkey");
            properties.setDefaultTtl(Duration.ofSeconds(10));
            String channel = "nexary:test:valkey:invalidation:" + UUID.randomUUID();
            RecordingObservationPublisher observationPublisher = new RecordingObservationPublisher();
            RedisCacheClient remote =
                    new RedisCacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, properties, observationPublisher);
            CacheInvalidationPublisher publisher =
                    new RedisCacheInvalidationPublisher(fixture.stringRedisTemplate, channel, observationPublisher, "valkey");
            TieredCacheClient nodeA = new TieredCacheClient(
                    new LocalCacheClient(Duration.ofSeconds(30)), remote, Duration.ofSeconds(30),
                    publisher, "valkey-node-a", observationPublisher);
            TieredCacheClient nodeB = new TieredCacheClient(
                    new LocalCacheClient(Duration.ofSeconds(30)), remote, Duration.ofSeconds(30),
                    CacheInvalidationPublisher.NOOP, "valkey-node-b", observationPublisher);
            RedisMessageListenerContainer container = listenerContainer(fixture.connectionFactory);
            RedisCacheInvalidationSubscriber subscriber = new RedisCacheInvalidationSubscriber(
                    container, nodeB, channel, "valkey-node-b", false, observationPublisher, "valkey");
            CacheKey key = CacheKey.of("valkey-tiered", UUID.randomUUID().toString());

            try {
                container.start();
                subscriber.start();
                remote.put(key, 1, Duration.ofSeconds(10));
                assertThat(nodeB.get(key, Integer.class)).contains(1);

                nodeA.put(key, 2, Duration.ofSeconds(10));

                assertEventually(() -> nodeB.get(key, Integer.class), Optional.of(2));
                assertThat(observationPublisher.events)
                        .extracting(event -> event.tags().get("operation"))
                        .contains("cache.invalidation_publish", "cache.invalidation_receive", "cache.invalidation_evict");
                assertBoundedTags(observationPublisher.events);
            } finally {
                subscriber.stop();
                container.stop();
                destroy(container);
            }
        }
    }

    private static final class ValkeyFixture implements AutoCloseable {
        private final LettuceConnectionFactory connectionFactory;
        private final RedisTemplate<String, Object> redisTemplate;
        private final StringRedisTemplate stringRedisTemplate;

        private ValkeyFixture() {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                    env("NEXARY_INFRA_VALKEY_HOST", "127.0.0.1"),
                    Integer.parseInt(env("NEXARY_INFRA_VALKEY_PORT", "16380")));
            this.connectionFactory = new LettuceConnectionFactory(configuration);
            this.connectionFactory.afterPropertiesSet();
            this.redisTemplate = new RedisTemplate<>();
            this.redisTemplate.setConnectionFactory(connectionFactory);
            this.redisTemplate.setKeySerializer(new StringRedisSerializer());
            this.redisTemplate.setValueSerializer(new JdkSerializationRedisSerializer());
            this.redisTemplate.afterPropertiesSet();
            this.stringRedisTemplate = new StringRedisTemplate(connectionFactory);
            this.stringRedisTemplate.afterPropertiesSet();
        }

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }

    private static RedisMessageListenerContainer listenerContainer(LettuceConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();
        return container;
    }

    private static void destroy(RedisMessageListenerContainer container) {
        try {
            container.destroy();
        } catch (Exception ex) {
            throw new AssertionError("Failed to destroy Valkey listener container", ex);
        }
    }

    private static <T> void assertEventually(Supplier<Optional<T>> supplier, Optional<T> expected) {
        AssertionError lastError = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                assertThat(supplier.get()).isEqualTo(expected);
                return;
            } catch (AssertionError error) {
                lastError = error;
                sleep(Duration.ofMillis(50));
            }
        }
        if (lastError != null) {
            throw lastError;
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for Valkey invalidation", ex);
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

    private static void assertBoundedTags(List<NexaryObservationEvent> events) {
        assertThat(events).allSatisfy(event -> {
            assertThat(event.tags()).containsEntry("capability", "cache");
            assertThat(event.tags()).containsKeys("operation", "provider", "tier", "outcome", "failure");
            assertThat(event.tags()).doesNotContainKeys(
                    "cache_key", "lock_token", "owner_token", "fencing_token", "exception", "exception_message");
        });
    }

    private static final class RecordingObservationPublisher implements NexaryObservationPublisher {
        private final List<NexaryObservationEvent> events = new ArrayList<>();

        @Override
        public void publish(NexaryObservationEvent event) {
            events.add(event);
        }
    }
}
