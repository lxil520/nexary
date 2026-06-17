package org.nexary.job.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.nexary.cache.redis.RedisCacheClient;
import org.nexary.cache.redis.RedisCacheProperties;
import org.nexary.job.JobSchedule;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

class CacheBackedLocalJobWorkerRegistryIntegrationTest {
    @Test
    void registersAndExpiresWorkersAgainstRealRedis() throws Exception {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            String topology = "job-registry-" + UUID.randomUUID();
            RedisCacheProperties cacheProperties = new RedisCacheProperties();
            cacheProperties.setDefaultTtl(Duration.ofSeconds(5));
            cacheProperties.setLockRetryInterval(Duration.ofMillis(10));
            RedisCacheClient cacheClient =
                    new RedisCacheClient(fixture.redisTemplate, fixture.stringRedisTemplate, cacheProperties);

            CacheBackedLocalJobWorkerRegistry nodeA =
                    new CacheBackedLocalJobWorkerRegistry(Optional.of(cacheClient), properties("node-a", topology));
            CacheBackedLocalJobWorkerRegistry nodeB =
                    new CacheBackedLocalJobWorkerRegistry(Optional.of(cacheClient), properties("node-b", topology));

            nodeA.heartbeat();
            nodeB.heartbeat();

            JobSchedule schedule = new JobSchedule("sample-job", "0 */10 * * * *", false, 2);
            assertThat(nodeA.workerIds(schedule)).containsExactly("node-a", "node-b");

            Thread.sleep(80);
            nodeB.heartbeat();

            assertThat(nodeB.workerIds(schedule)).containsExactly("node-b");
        }
    }

    private static LocalJobSchedulerProperties properties(String workerId, String topology) {
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        properties.setWorkerId(workerId);
        properties.setTopology(topology);
        properties.setHeartbeatTtl(Duration.ofMillis(50));
        properties.setHeartbeatInterval(Duration.ofMillis(10));
        return properties;
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
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean infraTestsEnabled() {
        return Boolean.parseBoolean(env("NEXARY_RUN_INFRA_TESTS", "false"));
    }
}
