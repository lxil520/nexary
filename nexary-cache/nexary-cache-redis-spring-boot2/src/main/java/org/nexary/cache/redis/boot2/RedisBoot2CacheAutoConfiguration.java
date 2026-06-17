package org.nexary.cache.redis.boot2;

import java.util.List;
import org.nexary.cache.CacheClient;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Spring Boot 2 auto-configuration for Redis cache support. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(prefix = "nexary.cache", name = "provider", havingValue = "redis", matchIfMissing = true)
@EnableConfigurationProperties(RedisBoot2CacheProperties.class)
public class RedisBoot2CacheAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public NexaryObservationPublisher nexaryObservationPublisher(List<NexaryObservationListener> listeners) {
        return NexaryObservationPublisher.fanOut(listeners);
    }

    @Bean(autowireCandidate = false)
    @ConditionalOnBean({RedisTemplate.class, StringRedisTemplate.class})
    @ConditionalOnMissingBean(RedisBoot2CacheClient.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisBoot2CacheClient redisBoot2CacheClient(
            RedisTemplate redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisBoot2CacheProperties properties,
            NexaryObservationPublisher observationPublisher) {
        assertTieredUnavailable(properties);
        return new RedisBoot2CacheClient(redisTemplate, stringRedisTemplate, properties, observationPublisher);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(CacheCounterClient.class)
    public CacheCounterClient cacheCounterClient(
            StringRedisTemplate stringRedisTemplate,
            NexaryObservationPublisher observationPublisher) {
        return new RedisBoot2CacheCounterClient(stringRedisTemplate, observationPublisher);
    }

    @Bean
    @ConditionalOnBean({RedisTemplate.class, StringRedisTemplate.class})
    @ConditionalOnMissingBean(value = CacheClient.class, ignored = RedisBoot2CacheClient.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public CacheClient primaryCacheClient(
            RedisTemplate redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisBoot2CacheProperties properties,
            NexaryObservationPublisher observationPublisher) {
        assertTieredUnavailable(properties);
        return new RedisBoot2CacheClient(redisTemplate, stringRedisTemplate, properties, observationPublisher);
    }

    private void assertTieredUnavailable(RedisBoot2CacheProperties properties) {
        if (properties.isTieredEnabled()) {
            throw new IllegalStateException(
                    "nexary-cache-redis-spring-boot2 does not include tiered local cache. "
                            + "Keep nexary.cache.redis.tiered-enabled=false on the Boot2 line until a Caffeine2 module is verified.");
        }
    }
}
