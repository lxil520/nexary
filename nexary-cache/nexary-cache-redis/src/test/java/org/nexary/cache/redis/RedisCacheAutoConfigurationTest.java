package org.nexary.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.cache.invalidation.CacheInvalidationListener;
import org.nexary.cache.redis.invalidation.RedisCacheInvalidationSubscriber;
import org.nexary.cache.tiered.TieredCacheClient;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

class RedisCacheAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisCacheAutoConfiguration.class))
            .withUserConfiguration(RedisInfrastructureConfiguration.class);

    @Test
    void redisOnlyDefaultDoesNotCreateLocalTierOrInvalidationListener() {
        contextRunner.run(context -> {
            assertThat(context.getBean("primaryCacheClient", CacheClient.class)).isInstanceOf(RedisCacheClient.class);
            assertThat(context.getBean(CacheCounterClient.class)).isInstanceOf(RedisCacheCounterClient.class);
            assertThat(context).doesNotHaveBean(RedisCacheInvalidationSubscriber.class);
        });
    }

    @Test
    void valkeyProviderUsesRedisProtocolCacheClient() {
        contextRunner
                .withPropertyValues("nexary.cache.provider=valkey")
                .run(context -> {
                    assertThat(context.getBean("primaryCacheClient", CacheClient.class)).isInstanceOf(RedisCacheClient.class);
                    assertThat(context.getBean(CacheCounterClient.class)).isInstanceOf(RedisCacheCounterClient.class);
                    assertThat(context.getBean(RedisCacheProperties.class).getProviderName()).isEqualTo("valkey");
                });
    }

    @Test
    void unsupportedProviderDoesNotCreateCacheClient() {
        contextRunner
                .withPropertyValues("nexary.cache.provider=memcached")
                .run(context -> assertThat(context).doesNotHaveBean(CacheClient.class));
    }

    @Test
    void governanceRuntimeWrapsPrimaryCacheClient() {
        contextRunner
                .withUserConfiguration(GovernanceRuntimeConfiguration.class)
                .run(context -> assertThat(context.getBean("primaryCacheClient", CacheClient.class))
                        .isInstanceOf(GovernedCacheClient.class));
    }

    @Test
    void governanceRuntimeKeepsTieredInvalidationListenerVisible() {
        contextRunner
                .withUserConfiguration(GovernanceRuntimeConfiguration.class)
                .withPropertyValues("nexary.cache.redis.tiered-enabled=true")
                .run(context -> {
                    assertThat(context.getBean("primaryCacheClient", CacheClient.class))
                            .isInstanceOf(CacheInvalidationListener.class);
                    assertThat(context).hasSingleBean(RedisCacheInvalidationSubscriber.class);
                });
    }

    @Test
    void tieredOptInCreatesLocalTierAndInvalidationListenerByDefault() {
        contextRunner
                .withPropertyValues("nexary.cache.redis.tiered-enabled=true")
                .run(context -> {
                    assertThat(context.getBean("primaryCacheClient", CacheClient.class)).isInstanceOf(TieredCacheClient.class);
                    assertThat(context.getBean(CacheCounterClient.class)).isInstanceOf(RedisCacheCounterClient.class);
                    assertThat(context).hasSingleBean(RedisCacheInvalidationSubscriber.class);
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
                    assertThat(context).doesNotHaveBean(RedisCacheInvalidationSubscriber.class);
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

    @Configuration(proxyBeanMethods = false)
    static class GovernanceRuntimeConfiguration {
        @Bean
        GovernanceRuntime governanceRuntime() {
            return new GovernanceRuntime() {
                @Override
                public <T> T execute(GovernanceContext context, Callable<T> action) throws Exception {
                    return action.call();
                }

                @Override
                public <T> T execute(GovernanceContext context, Callable<T> action, Callable<T> fallback) throws Exception {
                    return action.call();
                }
            };
        }
    }
}
