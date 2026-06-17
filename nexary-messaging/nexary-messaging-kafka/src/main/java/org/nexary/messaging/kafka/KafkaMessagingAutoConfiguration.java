package org.nexary.messaging.kafka;

import java.util.ArrayList;
import java.util.stream.Collectors;
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

/** Spring Boot auto-configuration for Kafka messaging support. */
@AutoConfiguration
@EnableConfigurationProperties(KafkaMessagingProperties.class)
public class KafkaMessagingAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "nexary.messaging",
            name = "provider",
            havingValue = "kafka")
    public MessageDeduplicationStore kafkaMessageDeduplicationStore() {
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
            KafkaMessagingProperties properties) {
        return new MessageConsumeExecutor(
                java.util.Optional.ofNullable(deduplicationStore.getIfAvailable()),
                properties.getDeduplicationTtl(),
                interceptors.orderedStream().collect(Collectors.toCollection(ArrayList::new)),
                properties.toRetryPolicy(),
                deadLetterPublisher.getIfAvailable(MessageDeadLetterPublisher::inMemory),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }

    @Bean
    @ConditionalOnBean(name = "kafkaTemplate")
    @ConditionalOnMissingBean(name = "kafkaMessagePublisher")
    public MessagePublisher kafkaMessagePublisher(
            @Qualifier("kafkaTemplate") Object kafkaTemplate,
            ObjectProvider<MessageSerializer> serializer,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new KafkaMessagePublisher(
                kafkaTemplate,
                serializer.getIfAvailable(DefaultStringMessageSerializer::new),
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }

    @Bean
    @ConditionalOnMissingBean(KafkaMessageListenerAdapterFactory.class)
    public KafkaMessageListenerAdapterFactory kafkaMessageListenerAdapterFactory(
            MessageConsumeExecutor consumeExecutor,
            MessageSerializer serializer,
            ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new KafkaMessageListenerAdapterFactory(
                consumeExecutor,
                serializer,
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }
}
