package org.nexary.cache.redis.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collections;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.cache.counter.CacheCounterKey;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class RedisBoot2CacheClientIntegrationTest {
    @Test
    void storesValuesLocksAndCountersAgainstRealRedis() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisBoot2CacheProperties properties = new RedisBoot2CacheProperties();
            properties.setDefaultTtl(Duration.ofSeconds(5));
            properties.setLockRetryInterval(Duration.ofMillis(20));
            RedisBoot2CacheClient client =
                    new RedisBoot2CacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, properties);
            RedisBoot2CacheCounterClient counterClient =
                    new RedisBoot2CacheCounterClient(fixture.stringRedisTemplate);
            CacheKey cacheKey = CacheKey.of("boot2-cache", UUID.randomUUID().toString());
            CacheKey lockKey = CacheKey.of("boot2-lock", UUID.randomUUID().toString());
            CacheCounterKey counterKey = CacheCounterKey.of("boot2-counter", UUID.randomUUID().toString());

            client.put(cacheKey, "Alice", Duration.ofSeconds(5));

            assertThat(client.get(cacheKey, String.class)).contains("Alice");
            assertThat(client.getAll(Collections.singletonList(cacheKey))).containsKey(cacheKey);
            assertThat(client.expire(cacheKey, Duration.ofSeconds(10))).isTrue();

            LockHandle lock = client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(2))
                    .orElseThrow(() -> new AssertionError("Expected Redis lock"));
            assertThat(lock.fencingToken()).isPresent();
            assertThat(lock.renew(Duration.ofSeconds(3))).isTrue();
            lock.close();
            assertThat(client.tryLock(lockKey, Duration.ZERO, Duration.ofSeconds(1))).isPresent();

            assertThat(counterClient.increment(counterKey, 2, Duration.ofSeconds(5)).value()).isEqualTo(2);
            assertThat(counterClient.decrement(counterKey, 1, Duration.ofSeconds(5)).value()).isEqualTo(1);
            OptionalLong current = counterClient.current(counterKey);
            assertThat(current).hasValue(1);
            assertThat(counterClient.clear(counterKey)).isTrue();
            assertThat(client.delete(cacheKey)).isTrue();
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
}
