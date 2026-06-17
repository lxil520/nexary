package org.nexary.cache.redis.boot4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.cache.redis.boot4.invalidation.RedisBoot4CacheInvalidationSubscriber;
import org.nexary.cache.tiered.TieredCacheClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class RedisBoot4CacheAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisBoot4CacheAutoConfiguration.class))
            .withUserConfiguration(RedisInfrastructureConfiguration.class);

    @Test
    void redisOnlyDefaultDoesNotCreateLocalTierOrInvalidationListener() {
        contextRunner.run(context -> {
            assertThat(context.getBean("primaryCacheClient", CacheClient.class)).isInstanceOf(RedisBoot4CacheClient.class);
            assertThat(context.getBean(CacheCounterClient.class)).isInstanceOf(RedisBoot4CacheCounterClient.class);
            assertThat(context).doesNotHaveBean(RedisBoot4CacheInvalidationSubscriber.class);
        });
    }

    @Test
    void tieredOptInCreatesLocalTierAndInvalidationListenerByDefault() {
        contextRunner
                .withPropertyValues("nexary.cache.redis.tiered-enabled=true")
                .run(context -> {
                    assertThat(context.getBean("primaryCacheClient", CacheClient.class)).isInstanceOf(TieredCacheClient.class);
                    assertThat(context.getBean(CacheCounterClient.class)).isInstanceOf(RedisBoot4CacheCounterClient.class);
                    assertThat(context).hasSingleBean(RedisBoot4CacheInvalidationSubscriber.class);
                });
    }

    @Test
    void tieredOptInCanDisableInvalidationListener() {
        contextRunner
                .withPropertyValues(
                        "nexary.cache.redis.tiered-enabled=true",
                        "nexary.cache.redis.invalidation-enabled=false")
                .run(context -> {
                    assertThat(context.getBean("primaryCacheClient", CacheClient.class)).isInstanceOf(TieredCacheClient.class);
                    assertThat(context).doesNotHaveBean(RedisBoot4CacheInvalidationSubscriber.class);
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

        @Bean
        RedisMessageListenerContainer redisMessageListenerContainer() {
            return mock(RedisMessageListenerContainer.class);
        }
    }
}
