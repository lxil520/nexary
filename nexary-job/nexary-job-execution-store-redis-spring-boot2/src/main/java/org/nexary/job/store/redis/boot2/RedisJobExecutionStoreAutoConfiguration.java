package org.nexary.job.store.redis.boot2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.execution.JobExecutionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Auto-configuration for Redis-backed durable job execution records. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "nexary.job.execution.store.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RedisJobExecutionStoreProperties.class)
public class RedisJobExecutionStoreAutoConfiguration {
    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(JobExecutionStore.class)
    public JobExecutionStore redisJobExecutionStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectProvider<ObjectMapper> objectMapper,
            RedisJobExecutionStoreProperties properties,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<NexaryObservationListener> observationListeners) {
        ObjectMapper mapper = objectMapper.getIfAvailable(this::defaultObjectMapper).copy();
        mapper.registerModule(new JavaTimeModule());
        return new RedisJobExecutionStore(
                stringRedisTemplate,
                mapper,
                properties,
                observationPublisher(observationPublisher, observationListeners));
    }

    private ObjectMapper defaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    private NexaryObservationPublisher observationPublisher(
            ObjectProvider<NexaryObservationPublisher> publisher,
            ObjectProvider<NexaryObservationListener> listeners) {
        return publisher.getIfAvailable(() -> NexaryObservationPublisher.fanOut(
                org.nexary.job.internal.JobCompatibilityCollections.collectList(listeners.orderedStream())));
    }
}
