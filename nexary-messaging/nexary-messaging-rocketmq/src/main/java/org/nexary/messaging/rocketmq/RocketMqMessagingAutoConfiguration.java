package org.nexary.messaging.rocketmq;

import java.util.ArrayList;
import java.util.stream.Collectors;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageDeduplicationStore;
import org.nexary.messaging.MessageInterceptor;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for RocketMQ messaging support. */
@AutoConfiguration
@EnableConfigurationProperties(RocketMqMessagingProperties.class)
public class RocketMqMessagingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "nexary.messaging",
            name = "provider",
            havingValue = "rocketmq")
    public MessageDeduplicationStore rocketMqMessageDeduplicationStore() {
        return new ConcurrentMapMessageDeduplicationStore();
    }

    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer messageSerializer() {
        return new DefaultStringMessageSerializer();
    }

    @Bean
    @ConditionalOnMissingBean(MessageConsumeExecutor.class)
    public MessageConsumeExecutor messageConsumeExecutor(
            ObjectProvider<MessageDeduplicationStore> deduplicationStore,
            ObjectProvider<MessageInterceptor> interceptors,
            ObjectProvider<MessageDeadLetterPublisher> deadLetterPublisher,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<GovernanceExecution> governanceExecution,
            RocketMqMessagingProperties properties) {
        return new MessageConsumeExecutor(
                java.util.Optional.ofNullable(deduplicationStore.getIfAvailable()),
                properties.getDeduplicationTtl(),
                interceptors.orderedStream().collect(Collectors.toCollection(ArrayList::new)),
                properties.toRetryPolicy(),
                deadLetterPublisher.getIfAvailable(MessageDeadLetterPublisher::inMemory),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop),
                governanceExecution.getIfAvailable(GovernanceExecution::direct),
                "rocketmq");
    }

    @Bean
    @ConditionalOnBean(name = "rocketMQTemplate")
    @ConditionalOnMissingBean(name = "rocketMqMessagePublisher")
    public MessagePublisher rocketMqMessagePublisher(
            @Qualifier("rocketMQTemplate") Object rocketMqTemplate,
            ObjectProvider<MessageSerializer> serializer,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new RocketMqMessagePublisher(
                rocketMqTemplate,
                serializer.getIfAvailable(DefaultStringMessageSerializer::new),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }

    @Bean
    @ConditionalOnMissingBean(RocketMqMessageListenerAdapterFactory.class)
    public RocketMqMessageListenerAdapterFactory rocketMqMessageListenerAdapterFactory(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new RocketMqMessageListenerAdapterFactory(
                consumeExecutor,
                serializer,
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }
}
