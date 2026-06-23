package org.nexary.messaging.redis.boot2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageDeduplicationStore;
import org.nexary.messaging.MessageInterceptor;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSerializer;
import org.nexary.messaging.MessageSubscriber;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Spring Boot 2 auto-configuration for Redis queue messaging support. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "nexary.messaging.redis", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RedisBoot2MessagingProperties.class)
public class RedisBoot2MessagingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public NexaryObservationPublisher nexaryObservationPublisher(List<NexaryObservationListener> listeners) {
        return NexaryObservationPublisher.fanOut(listeners);
    }

    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer redisBoot2MessageSerializer() {
        return new DefaultStringMessageSerializer();
    }

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    public MessageDeduplicationStore redisBoot2MessageDeduplicationStore(
            StringRedisTemplate stringRedisTemplate,
            RedisBoot2MessagingProperties properties) {
        return new RedisBoot2MessageDeduplicationStore(stringRedisTemplate, properties);
    }

    @Bean
    @ConditionalOnMissingBean(MessageConsumeExecutor.class)
    public MessageConsumeExecutor redisBoot2MessageConsumeExecutor(
            ObjectProvider<MessageDeduplicationStore> deduplicationStore,
            ObjectProvider<MessageInterceptor> interceptors,
            ObjectProvider<MessageDeadLetterPublisher> deadLetterPublisher,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<GovernanceExecution> governanceExecution,
            RedisBoot2MessagingProperties properties) {
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
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(RedisBoot2MessageQueue.class)
    public RedisBoot2MessageQueue redisBoot2MessageQueue(
            StringRedisTemplate stringRedisTemplate,
            MessageSerializer serializer,
            MessageConsumeExecutor consumeExecutor,
            RedisBoot2MessagingProperties properties,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new RedisBoot2MessageQueue(
                stringRedisTemplate,
                serializer,
                consumeExecutor,
                properties,
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }

    @Bean
    @ConditionalOnMissingBean(MessagePublisher.class)
    public MessagePublisher redisBoot2MessagePublisher(RedisBoot2MessageQueue queue) {
        return queue;
    }

    @Bean
    @ConditionalOnMissingBean(MessageSubscriber.class)
    public MessageSubscriber redisBoot2MessageSubscriber(RedisBoot2MessageQueue queue) {
        return queue;
    }
}
