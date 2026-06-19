package org.nexary.messaging.activemqclassic;

import java.time.Duration;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageRetryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** ActiveMQ Classic messaging adapter settings. */
@ConfigurationProperties(prefix = "nexary.messaging.activemq-classic")
public class ActiveMqClassicMessagingProperties {
    private boolean enabled = true;
    private String brokerUrl = "tcp://127.0.0.1:61616";
    private Duration receiveTimeout = Duration.ofSeconds(1);
    private Duration deduplicationTtl = Duration.ofHours(1);
    private int retryMaxAttempts = 3;
    private Duration retryInitialDelay = Duration.ofSeconds(1);
    private MessageBackoffStrategy retryBackoffStrategy = MessageBackoffStrategy.FIXED;
    private double retryBackoffMultiplier = 2.0d;
    private Duration retryMaxBackoff = Duration.ofSeconds(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public Duration getReceiveTimeout() {
        return receiveTimeout;
    }

    public void setReceiveTimeout(Duration receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }

    public Duration getDeduplicationTtl() {
        return deduplicationTtl;
    }

    public void setDeduplicationTtl(Duration deduplicationTtl) {
        this.deduplicationTtl = deduplicationTtl;
    }

    public int getRetryMaxAttempts() {
        return retryMaxAttempts;
    }

    public void setRetryMaxAttempts(int retryMaxAttempts) {
        this.retryMaxAttempts = retryMaxAttempts;
    }

    public Duration getRetryInitialDelay() {
        return retryInitialDelay;
    }

    public void setRetryInitialDelay(Duration retryInitialDelay) {
        this.retryInitialDelay = retryInitialDelay;
    }

    public MessageBackoffStrategy getRetryBackoffStrategy() {
        return retryBackoffStrategy;
    }

    public void setRetryBackoffStrategy(MessageBackoffStrategy retryBackoffStrategy) {
        this.retryBackoffStrategy = retryBackoffStrategy;
    }

    public double getRetryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    public void setRetryBackoffMultiplier(double retryBackoffMultiplier) {
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    public Duration getRetryMaxBackoff() {
        return retryMaxBackoff;
    }

    public void setRetryMaxBackoff(Duration retryMaxBackoff) {
        this.retryMaxBackoff = retryMaxBackoff;
    }

    public MessageRetryPolicy toRetryPolicy() {
        return new MessageRetryPolicy(
                retryMaxAttempts,
                retryInitialDelay,
                retryBackoffStrategy,
                retryBackoffMultiplier,
                retryMaxBackoff);
    }
}
