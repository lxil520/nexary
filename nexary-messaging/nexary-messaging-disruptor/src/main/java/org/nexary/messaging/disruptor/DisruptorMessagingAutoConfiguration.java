package org.nexary.messaging.disruptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeduplicationStore;
import org.nexary.messaging.MessageInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for in-process Disruptor-style messaging. */
@AutoConfiguration
@EnableConfigurationProperties(DisruptorMessagingProperties.class)
public class DisruptorMessagingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @ConditionalOnProperty(prefix = "nexary.messaging.disruptor", name = "enabled", havingValue = "true")
    public MessageDeduplicationStore disruptorMessageDeduplicationStore() {
        return new ConcurrentMapMessageDeduplicationStore();
    }

    @Bean
    @ConditionalOnMissingBean(MessageConsumeExecutor.class)
    public MessageConsumeExecutor messageConsumeExecutor(
            ObjectProvider<MessageDeduplicationStore> deduplicationStore,
            ObjectProvider<MessageInterceptor> interceptors,
            ObjectProvider<MessageDeadLetterPublisher> deadLetterPublisher,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            DisruptorMessagingProperties properties) {
        return new MessageConsumeExecutor(
                Optional.ofNullable(deduplicationStore.getIfAvailable()),
                Duration.ofHours(1),
                interceptors.stream().collect(Collectors.toCollection(ArrayList::new)),
                properties.toRetryPolicy(),
                deadLetterPublisher.getIfAvailable(MessageDeadLetterPublisher::inMemory),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }

    @Bean
    @ConditionalOnMissingBean(DisruptorMessageBus.class)
    @ConditionalOnProperty(prefix = "nexary.messaging.disruptor", name = "enabled", havingValue = "true")
    public DisruptorMessageBus disruptorMessageBus(
            DisruptorMessagingProperties properties,
            MessageConsumeExecutor consumeExecutor,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new DisruptorMessageBus(
                properties.getCapacity(),
                consumeExecutor,
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }
}
