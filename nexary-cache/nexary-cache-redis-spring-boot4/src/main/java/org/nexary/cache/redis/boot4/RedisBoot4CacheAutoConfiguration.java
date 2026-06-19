package org.nexary.cache.redis.boot4;

import java.util.List;
import org.nexary.cache.CacheClient;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.cache.invalidation.CacheInvalidationListener;
import org.nexary.cache.invalidation.CacheInvalidationPublisher;
import org.nexary.cache.redis.boot4.invalidation.RedisBoot4CacheInvalidationPublisher;
import org.nexary.cache.redis.boot4.invalidation.RedisBoot4CacheInvalidationSubscriber;
import org.nexary.cache.tiered.LocalCacheClient;
import org.nexary.cache.tiered.TieredCacheClient;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Spring Boot 4 auto-configuration for Redis cache support. */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.RedisAutoConfiguration")
@ConditionalOnClass(RedisTemplate.class)
@Conditional(RedisBoot4ProtocolCacheProviderCondition.class)
@EnableConfigurationProperties(RedisBoot4CacheProperties.class)
public class RedisBoot4CacheAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public NexaryObservationPublisher nexaryObservationPublisher(List<NexaryObservationListener> listeners) {
        return NexaryObservationPublisher.fanOut(listeners);
    }

    @Bean(autowireCandidate = false)
    @ConditionalOnBean({RedisTemplate.class, StringRedisTemplate.class})
    @ConditionalOnMissingBean(RedisBoot4CacheClient.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RedisBoot4CacheClient redisBoot4CacheClient(
            RedisTemplate redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisBoot4CacheProperties properties,
            Environment environment,
            NexaryObservationPublisher observationPublisher) {
        configureProvider(properties, environment);
        return new RedisBoot4CacheClient(redisTemplate, stringRedisTemplate, properties, observationPublisher);
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(CacheCounterClient.class)
    public CacheCounterClient cacheCounterClient(
            StringRedisTemplate stringRedisTemplate,
            RedisBoot4CacheProperties properties,
            Environment environment,
            NexaryObservationPublisher observationPublisher) {
        configureProvider(properties, environment);
        return new RedisBoot4CacheCounterClient(stringRedisTemplate, observationPublisher, properties.getProviderName());
    }

    @Bean
    @ConditionalOnBean({RedisTemplate.class, StringRedisTemplate.class})
    @ConditionalOnMissingBean(value = CacheClient.class, ignored = RedisBoot4CacheClient.class)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public CacheClient primaryCacheClient(
            RedisTemplate redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisBoot4CacheProperties properties,
            Environment environment,
            NexaryObservationPublisher observationPublisher) {
        configureProvider(properties, environment);
        RedisBoot4CacheClient redisCacheClient =
                new RedisBoot4CacheClient(redisTemplate, stringRedisTemplate, properties, observationPublisher);
        if (properties.isTieredEnabled()) {
            LocalCacheClient local = new LocalCacheClient(properties.getLocalTtl());
            CacheInvalidationPublisher publisher = properties.isInvalidationEnabled()
                    ? new RedisBoot4CacheInvalidationPublisher(
                            stringRedisTemplate,
                            properties.getInvalidationChannel(),
                            observationPublisher,
                            properties.getProviderName())
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
    @Conditional(CacheBoot4InvalidationEnabledCondition.class)
    public RedisMessageListenerContainer redisCacheInvalidationMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisBoot4CacheProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setRecoveryInterval(properties.getInvalidationListenerRecoveryBackoff().toMillis());
        return container;
    }

    @Bean
    @ConditionalOnBean({RedisMessageListenerContainer.class, CacheClient.class})
    @ConditionalOnMissingBean
    @Conditional(CacheBoot4InvalidationEnabledCondition.class)
    public RedisBoot4CacheInvalidationSubscriber redisCacheInvalidationSubscriber(
            RedisMessageListenerContainer container,
            CacheClient cacheClient,
            RedisBoot4CacheProperties properties,
            NexaryObservationPublisher observationPublisher) {
        if (!(cacheClient instanceof CacheInvalidationListener)) {
            throw new IllegalStateException("Cache invalidation requires a local-tier CacheClient");
        }
        CacheInvalidationListener listener = (CacheInvalidationListener) cacheClient;
        return new RedisBoot4CacheInvalidationSubscriber(
                container,
                listener,
                properties.getInvalidationChannel(),
                properties.getInvalidationOriginId(),
                properties.isInvalidationListenerAutoStart(),
                observationPublisher,
                properties.getProviderName());
    }

    private void configureProvider(RedisBoot4CacheProperties properties, Environment environment) {
        properties.setProviderName(environment.getProperty("nexary.cache.provider", RedisBoot4ProtocolCacheProviderCondition.REDIS));
    }
}
