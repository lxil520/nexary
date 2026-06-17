package org.nexary.samples.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.nexary.samples.cache.api.CacheSampleController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = org.nexary.samples.cache.app.CacheSampleApplication.class,
        properties = {
                "spring.profiles.active=redis",
                "spring.data.redis.host=${NEXARY_INFRA_REDIS_HOST:127.0.0.1}",
                "spring.data.redis.port=${NEXARY_INFRA_REDIS_PORT:16379}"
        })
class CacheSampleRedisProfileIntegrationTest {
    @Autowired
    private CacheSampleController controller;

    @Test
    void redisProfileRunsBusinessCacheUseCases() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");

        assertThat(controller.profile("redis-7").name()).isEqualTo("user-redis-7");
        assertThat(controller.warmup().size()).isEqualTo(2);
        assertThat(controller.batch("101,102")).hasSize(2);
        controller.deleteUserCount("redis-7");
        assertThat(controller.userCount("redis-7").count()).isZero();
        assertThat(controller.incrementUserCount("redis-7", 2).count()).isEqualTo(2);
        assertThat(controller.decrementUserCount("redis-7", 1).count()).isEqualTo(1);
        assertThat(controller.userCount("redis-7").count()).isEqualTo(1);
        assertThat(controller.deleteUserCount("redis-7").deleted()).isTrue();
        assertThat(controller.lock("redis-sample").acquired()).isTrue();
    }

    private static boolean infraTestsEnabled() {
        return Boolean.parseBoolean(env("NEXARY_RUN_INFRA_TESTS", "false"));
    }

    private static String env(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
