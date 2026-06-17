package org.nexary.cache.redis;

import java.util.List;
import org.nexary.cache.CacheClient;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.cache.invalidation.CacheInvalidationListener;
import org.nexary.cache.invalidation.CacheInvalidationPublisher;
import org.nexary.cache.redis.invalidation.RedisCacheInvalidationPublisher;
import org.nexary.cache.redis.invalidation.RedisCacheInvalidationSubscriber;
import org.nexary.cache.tiered.LocalCacheClient;
import org.nexary.cache.tiered.TieredCacheClient;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Spring Boot auto-configuration for Redis cache support. */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(prefix = "nexary.cache", name = "provider", havingValue = "redis", matchIfMissing = true)
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisCacheAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public NexaryObservationPublisher nexaryObservationPublisher(List<NexaryObservationListener> listeners) {
        return NexaryObservationPublisher.fanOut(listeners);
    }

    @Bean(autowireCandidate = false)
    @ConditionalOnBean({RedisTemplate.class, StringRedisTemplate.class})
    @ConditionalOnMissingBean(RedisCacheClient.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisCacheClient redisCacheClient(
            RedisTemplate redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisCacheProperties properties,
            NexaryObservationPublisher observationPublisher) {
        return new RedisCacheClient(redisTemplate, stringRedisTemplate, properties, observationPublisher);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(CacheCounterClient.class)
    public CacheCounterClient cacheCounterClient(
            StringRedisTemplate stringRedisTemplate,
            NexaryObservationPublisher observationPublisher) {
        return new RedisCacheCounterClient(stringRedisTemplate, observationPublisher);
    }

    @Bean
    @ConditionalOnBean({RedisTemplate.class, StringRedisTemplate.class})
    @ConditionalOnMissingBean(value = CacheClient.class, ignored = RedisCacheClient.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public CacheClient primaryCacheClient(
            RedisTemplate redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisCacheProperties properties,
            NexaryObservationPublisher observationPublisher) {
        RedisCacheClient redisCacheClient =
                new RedisCacheClient(redisTemplate, stringRedisTemplate, properties, observationPublisher);
        if (properties.isTieredEnabled()) {
            LocalCacheClient local = new LocalCacheClient(properties.getLocalTtl());
            CacheInvalidationPublisher publisher = properties.isInvalidationEnabled()
                    ? new RedisCacheInvalidationPublisher(
                            stringRedisTemplate, properties.getInvalidationChannel(), observationPublisher)
                    : CacheInvalidationPublisher.NOOP;
            return new TieredCacheClient(
                    local,
                    redisCacheClient,
                    properties.getLocalTtl(),
                    publisher,
                    properties.getInvalidationOriginId(),
                    observationPublisher);
        }
        return redisCacheClient;
    }

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean
    @Conditional(CacheInvalidationEnabledCondition.class)
    public RedisMessageListenerContainer redisCacheInvalidationMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisCacheProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(properties.getInvalidationListenerRecoveryBackoff().toMillis());
        return container;
    }

    @Bean
    @ConditionalOnBean({RedisMessageListenerContainer.class, CacheClient.class})
    @ConditionalOnMissingBean
    @Conditional(CacheInvalidationEnabledCondition.class)
    public RedisCacheInvalidationSubscriber redisCacheInvalidationSubscriber(
            RedisMessageListenerContainer container,
            CacheClient cacheClient,
            RedisCacheProperties properties,
            NexaryObservationPublisher observationPublisher) {
        if (!(cacheClient instanceof CacheInvalidationListener)) {
            throw new IllegalStateException("Cache invalidation requires a local-tier CacheClient");
        }
        CacheInvalidationListener listener = (CacheInvalidationListener) cacheClient;
        return new RedisCacheInvalidationSubscriber(
                container,
                listener,
                properties.getInvalidationChannel(),
                properties.getInvalidationOriginId(),
                properties.isInvalidationListenerAutoStart(),
                observationPublisher);
    }
}
