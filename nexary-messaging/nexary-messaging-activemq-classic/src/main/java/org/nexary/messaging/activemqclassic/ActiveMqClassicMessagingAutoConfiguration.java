package org.nexary.messaging.activemqclassic;

import jakarta.jms.ConnectionFactory;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageDeduplicationStore;
import org.nexary.messaging.MessageInterceptor;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for ActiveMQ Classic messaging support. */
@AutoConfiguration
@EnableConfigurationProperties(ActiveMqClassicMessagingProperties.class)
public class ActiveMqClassicMessagingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "activemq-classic")
    public MessageDeduplicationStore activeMqClassicMessageDeduplicationStore() {
        return new ConcurrentMapMessageDeduplicationStore();
    }

    @Bean
    @ConditionalOnMissingBean(MessageSerializer.class)
    public MessageSerializer activeMqClassicMessageSerializer() {
        return new DefaultStringMessageSerializer();
    }

    @Bean
    @ConditionalOnMissingBean(MessageConsumeExecutor.class)
    public MessageConsumeExecutor activeMqClassicMessageConsumeExecutor(
            ObjectProvider<MessageDeduplicationStore> deduplicationStore,
            ObjectProvider<MessageInterceptor> interceptors,
            ObjectProvider<MessageDeadLetterPublisher> deadLetterPublisher,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<GovernanceExecution> governanceExecution,
            ActiveMqClassicMessagingProperties properties) {
        return new MessageConsumeExecutor(
                Optional.ofNullable(deduplicationStore.getIfAvailable()),
                properties.getDeduplicationTtl(),
                interceptors.orderedStream().collect(Collectors.toCollection(ArrayList::new)),
                properties.toRetryPolicy(),
                deadLetterPublisher.getIfAvailable(MessageDeadLetterPublisher::inMemory),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop),
                governanceExecution.getIfAvailable(GovernanceExecution::direct),
                "activemq_classic");
    }

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnMissingBean(name = "activeMqClassicMessagePublisher")
    public MessagePublisher activeMqClassicMessagePublisher(
            ConnectionFactory connectionFactory,
            ObjectProvider<MessageSerializer> serializer,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<GovernanceExecution> governanceExecution) {
        return new ActiveMqClassicMessagePublisher(
                connectionFactory,
                serializer.getIfAvailable(DefaultStringMessageSerializer::new),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop),
                governanceExecution.getIfAvailable(GovernanceExecution::direct));
    }

    @Bean
    @ConditionalOnMissingBean(ActiveMqClassicMessageListenerAdapterFactory.class)
    public ActiveMqClassicMessageListenerAdapterFactory activeMqClassicMessageListenerAdapterFactory(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new ActiveMqClassicMessageListenerAdapterFactory(
                consumeExecutor,
                serializer,
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }
}
