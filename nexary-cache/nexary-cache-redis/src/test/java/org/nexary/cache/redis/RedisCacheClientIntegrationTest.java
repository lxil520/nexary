package org.nexary.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
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

class RedisCacheClientIntegrationTest {
    @Test
    void storesValuesAndRenewsLocksAgainstRealRedis() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisCacheProperties properties = new RedisCacheProperties();
            properties.setDefaultTtl(Duration.ofSeconds(5));
            properties.setLockRetryInterval(Duration.ofMillis(20));
            RecordingObservationPublisher observationPublisher = new RecordingObservationPublisher();
            RedisCacheClient client = new RedisCacheClient(
                    fixture.redisTemplate, fixture.stringRedisTemplate, properties, observationPublisher);
            CacheKey cacheKey = CacheKey.of("infra-cache", UUID.randomUUID().toString());
            CacheKey lockKey = CacheKey.of("infra-lock", UUID.randomUUID().toString());

            client.put(cacheKey, "Alice", Duration.ofSeconds(5));

            assertThat(client.get(cacheKey, String.class)).contains("Alice");
            assertThat(client.get(CacheKey.of("infra-cache", UUID.randomUUID().toString()), String.class)).isEmpty();
            assertThat(client.expire(cacheKey, Duration.ofSeconds(10))).isTrue();
            assertThat(client.getAll(List.of(cacheKey))).containsKey(cacheKey);
            client.putAll(Map.of(CacheKey.of("infra-cache", UUID.randomUUID().toString()), "Bob"), Duration.ofSeconds(5));

            LockHandle lock = client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(2)).orElseThrow();
            assertThat(lock.fencingToken()).isPresent();
            assertThat(lock.renew(Duration.ofSeconds(3))).isTrue();
            lock.close();
            assertThat(client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(1))).isPresent();
            assertThat(client.delete(cacheKey)).isTrue();

            assertThat(observationPublisher.events)
                    .extracting(event -> event.tags().get("operation"))
                    .contains(
                            "cache.put",
                            "cache.get",
                            "cache.expire",
                            "cache.batch_get",
                            "cache.batch_put",
                            "cache.lock_acquire",
                            "cache.lock_renew",
                            "cache.lock_release",
                            "cache.delete");
            assertThat(observationPublisher.events)
                    .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "hit"))
                    .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "miss"))
                    .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "acquired"))
                    .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "success"));
            assertBoundedRedisTags(observationPublisher.events);
        }
    }

    @Test
    void lockFencingTokenIsMonotonicPerResourceAndUnlockRemainsOwnerSafe() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisCacheProperties properties = new RedisCacheProperties();
            properties.setDefaultTtl(Duration.ofSeconds(5));
            properties.setLockRetryInterval(Duration.ofMillis(20));
            RedisCacheClient client = new RedisCacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, properties);
            CacheKey firstKey = CacheKey.of("infra-lock-fence", UUID.randomUUID().toString());
            CacheKey secondKey = CacheKey.of("infra-lock-fence", UUID.randomUUID().toString());

            LockHandle first = client.tryLock(firstKey, Duration.ZERO, Duration.ofMillis(250)).orElseThrow();
            long firstToken = first.fencingToken().orElseThrow();
            first.close();
            LockHandle second = client.tryLock(firstKey, Duration.ZERO, Duration.ofSeconds(2)).orElseThrow();
            long secondToken = second.fencingToken().orElseThrow();
            LockHandle otherResource = client.tryLock(secondKey, Duration.ZERO, Duration.ofSeconds(2)).orElseThrow();

            assertThat(secondToken).isGreaterThan(firstToken);
            assertThat(otherResource.fencingToken()).hasValue(1);

            second.close();
            otherResource.close();
        }
    }

    @Test
    void staleOwnerCannotUnlockNewOwnerAfterLeaseExpiry() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisCacheProperties properties = new RedisCacheProperties();
            properties.setDefaultTtl(Duration.ofSeconds(5));
            properties.setLockRetryInterval(Duration.ofMillis(20));
            RedisCacheClient client = new RedisCacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, properties);
            CacheKey lockKey = CacheKey.of("infra-lock-owner", UUID.randomUUID().toString());

            LockHandle staleOwner = client.tryLock(lockKey, Duration.ZERO, Duration.ofMillis(150)).orElseThrow();
            sleep(Duration.ofMillis(220));
            LockHandle currentOwner = client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(2)).orElseThrow();

            staleOwner.close();

            assertThat(client.tryLock(lockKey, Duration.ZERO, Duration.ofMillis(100))).isEmpty();

            currentOwner.close();
            assertThat(client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(1))).isPresent();
        }
    }

    @Test
    void pubSubInvalidationLetsAnotherNodeRefreshLocalTierBeforeLocalTtlExpires() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisCacheProperties properties = new RedisCacheProperties();
            properties.setDefaultTtl(Duration.ofSeconds(10));
            String channel = "nexary:test:cache:invalidation:" + UUID.randomUUID();
            RecordingObservationPublisher observationPublisher = new RecordingObservationPublisher();
            RedisCacheClient remote =
                    new RedisCacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, properties, observationPublisher);
            CacheInvalidationPublisher publisher =
                    new RedisCacheInvalidationPublisher(fixture.stringRedisTemplate, channel, observationPublisher);
            TieredCacheClient nodeA = new TieredCacheClient(
                    new LocalCacheClient(Duration.ofSeconds(30)),
                    remote,
                    Duration.ofSeconds(30),
                    publisher,
                    "node-a",
                    observationPublisher);
            TieredCacheClient nodeB = new TieredCacheClient(
                    new LocalCacheClient(Duration.ofSeconds(30)), remote, Duration.ofSeconds(30),
                    CacheInvalidationPublisher.NOOP, "node-b", observationPublisher);
            RedisMessageListenerContainer container = listenerContainer(fixture.connectionFactory);
            RedisCacheInvalidationSubscriber subscriber =
                    new RedisCacheInvalidationSubscriber(container, nodeB, channel, "node-b", false, observationPublisher);
            CacheKey valueKey = CacheKey.of("infra-user-count", UUID.randomUUID().toString());
            CacheKey deleteKey = CacheKey.of("infra-user-count", UUID.randomUUID().toString());

            try {
                container.start();
                subscriber.start();

                remote.put(valueKey, 1, Duration.ofSeconds(10));
                assertThat(nodeB.get(valueKey, Integer.class)).contains(1);

                nodeA.put(valueKey, 2, Duration.ofSeconds(10));

                assertEventually(() -> nodeB.get(valueKey, Integer.class), Optional.of(2));

                remote.put(deleteKey, 3, Duration.ofSeconds(10));
                assertThat(nodeB.get(deleteKey, Integer.class)).contains(3);

                nodeA.delete(deleteKey);

                assertEventually(() -> nodeB.get(deleteKey, Integer.class), Optional.empty());

                assertThat(observationPublisher.events)
                        .extracting(event -> event.tags().get("operation"))
                        .contains(
                                "cache.invalidation_publish",
                                "cache.invalidation_receive",
                                "cache.invalidation_evict");
                assertThat(observationPublisher.events)
                        .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "published"))
                        .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "received"))
                        .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "evicted"));
            } finally {
                subscriber.stop();
                container.stop();
                destroy(container);
            }
        }
    }

    @Test
    void counterUsesRedisAtomicIncrementAndTtlOnCreateOnly() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RecordingObservationPublisher observationPublisher = new RecordingObservationPublisher();
            RedisCacheCounterClient counterClient =
                    new RedisCacheCounterClient(fixture.stringRedisTemplate, observationPublisher);
            CacheCounterKey key = CacheCounterKey.of("infra-counter", UUID.randomUUID().toString());

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
                    .extracting(event -> event.tags().get("operation"))
                    .contains(
                            "cache.counter_increment",
                            "cache.counter_decrement",
                            "cache.counter_current",
                            "cache.counter_clear");
            assertThat(observationPublisher.events)
                    .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "success"))
                    .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "hit"))
                    .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "miss"));
            assertBoundedRedisTags(observationPublisher.events);
        }
    }

    @Test
    void redisObservationPublisherIsNoopByDefault() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisCacheProperties properties = new RedisCacheProperties();
            RedisCacheClient client = new RedisCacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, properties);
            RedisCacheCounterClient counterClient = new RedisCacheCounterClient(fixture.stringRedisTemplate);
            CacheKey cacheKey = CacheKey.of("infra-noop", UUID.randomUUID().toString());
            CacheCounterKey counterKey = CacheCounterKey.of("infra-noop-counter", UUID.randomUUID().toString());

            client.put(cacheKey, "ok", Duration.ofSeconds(2));
            assertThat(client.get(cacheKey, String.class)).contains("ok");
            assertThat(counterClient.increment(counterKey, 1, Duration.ofSeconds(2)).value()).isEqualTo(1);
        }
    }

    private static final class RedisFixture implements AutoCloseable {
        private final LettuceConnectionFactory connectionFactory;
        private final RedisTemplate<String, Object> redisTemplate;
        private final StringRedisTemplate stringRedisTemplate;

        private RedisFixture() {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                    env("NEXARY_INFRA_REDIS_HOST", "127.0.0.1"),
                    Integer.parseInt(env("NEXARY_INFRA_REDIS_PORT", "16379")));
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
            throw new AssertionError("Failed to destroy Redis listener container", ex);
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
            throw new AssertionError("Interrupted while waiting for Redis invalidation", ex);
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

    private static void assertBoundedRedisTags(List<NexaryObservationEvent> events) {
        assertThat(events).allSatisfy(event -> {
            assertThat(event.tags()).containsEntry("capability", "cache");
            assertThat(event.tags()).containsEntry("provider", "redis");
            assertThat(event.tags()).containsKeys("operation", "tier", "outcome", "failure");
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
