package org.nexary.boot.messaging;

import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.MessageDeduplicationStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Common messaging starter defaults that are not provider-native wiring. */
@AutoConfiguration
@EnableConfigurationProperties(MessagingProviderSelectionProperties.class)
public class MessagingStarterCommonAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "disruptor", matchIfMissing = true)
    public MessageDeduplicationStore disruptorMessageDeduplicationStore() {
        return new ConcurrentMapMessageDeduplicationStore();
    }

    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "activemq-classic")
    public MessageDeduplicationStore activeMqClassicMessageDeduplicationStore() {
        return new ConcurrentMapMessageDeduplicationStore();
    }

    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "kafka")
    public MessageDeduplicationStore kafkaMessageDeduplicationStore() {
        return new ConcurrentMapMessageDeduplicationStore();
    }

    @Bean
    @ConditionalOnMissingBean(MessageDeduplicationStore.class)
    @ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "rocketmq")
    public MessageDeduplicationStore rocketMqMessageDeduplicationStore() {
        return new ConcurrentMapMessageDeduplicationStore();
    }
}
