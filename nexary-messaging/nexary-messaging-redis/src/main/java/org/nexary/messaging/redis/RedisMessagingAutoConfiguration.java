package org.nexary.messaging.redis;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageDeduplicationStore;
import org.nexary.messaging.MessageInterceptor;
import org.nexary.messaging.MessageSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Spring Boot auto-configuration for Redis queue messaging support. */
@AutoConfiguration(afterName = "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration")
@EnableConfigurationProperties(RedisMessagingProperties.class)
public class RedisMessagingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer redisMessageSerializer() {
        return new DefaultStringMessageSerializer();
    }

    @Bean
    @ConditionalOnProperty(prefix = "nexary.messaging.redis", name = "enabled", havingValue = "true")
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    public MessageDeduplicationStore redisMessageDeduplicationStore(
            StringRedisTemplate stringRedisTemplate,
            RedisMessagingProperties properties) {
        return new RedisMessageDeduplicationStore(stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConsumeExecutor.class)
    @ConditionalOnProperty(prefix = "nexary.messaging.redis", name = "enabled", havingValue = "true")
    public MessageConsumeExecutor messageConsumeExecutor(
            ObjectProvider<MessageDeduplicationStore> deduplicationStore,
            ObjectProvider<MessageInterceptor> interceptors,
            ObjectProvider<MessageDeadLetterPublisher> deadLetterPublisher,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<GovernanceExecution> governanceExecution,
            RedisMessagingProperties properties) {
        return new MessageConsumeExecutor(
                Optional.ofNullable(deduplicationStore.getIfAvailable()),
                properties.getDeduplicationTtl(),
                interceptors.orderedStream().collect(Collectors.toCollection(ArrayList::new)),
                properties.toRetryPolicy(),
                deadLetterPublisher.getIfAvailable(MessageDeadLetterPublisher::inMemory),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop),
                governanceExecution.getIfAvailable(GovernanceExecution::direct),
                "redis");
    }

    @Bean
    @ConditionalOnProperty(prefix = "nexary.messaging.redis", name = "enabled", havingValue = "true")
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RedisMessageQueue.class)
    public RedisMessageQueue redisMessageQueue(
            StringRedisTemplate stringRedisTemplate,
            MessageSerializer serializer,
            MessageConsumeExecutor consumeExecutor,
            RedisMessagingProperties properties,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new RedisMessageQueue(
                stringRedisTemplate,
                serializer,
                consumeExecutor,
                properties,
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }

}
