package org.nexary.cache.redis.boot2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.counter.CacheCounterClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisBoot2CacheAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisBoot2CacheAutoConfiguration.class))
            .withUserConfiguration(RedisInfrastructureConfiguration.class);

    @Test
    void redisOnlyDefaultCreatesRedisClientAndCounter() {
        contextRunner.run(context -> {
            assertThat(context.getBean("primaryCacheClient", CacheClient.class))
                    .isInstanceOf(RedisBoot2CacheClient.class);
            assertThat(context.getBean(CacheCounterClient.class))
                    .isInstanceOf(RedisBoot2CacheCounterClient.class);
        });
    }

    @Test
    void tieredModeFailsFastBecauseBoot2LineDoesNotShipCaffeine2Yet() {
        contextRunner
                .withPropertyValues("nexary.cache.redis.tiered-enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("does not include tiered local cache");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisInfrastructureConfiguration {
        @Bean
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
