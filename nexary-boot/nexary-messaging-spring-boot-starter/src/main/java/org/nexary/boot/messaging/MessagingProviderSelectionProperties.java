package org.nexary.boot.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Messaging starter provider selector settings. */
@ConfigurationProperties(prefix = "nexary.messaging")
public class MessagingProviderSelectionProperties {
    private Provider provider = Provider.DISRUPTOR;

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    /** Supported single-provider starter modes. */
    public enum Provider {
        DISRUPTOR,
        ACTIVEMQ_CLASSIC,
        REDIS,
        KAFKA,
        ROCKETMQ
    }
}
