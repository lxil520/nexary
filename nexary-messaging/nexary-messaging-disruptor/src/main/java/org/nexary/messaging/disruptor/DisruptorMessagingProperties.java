package org.nexary.messaging.disruptor;

import java.time.Duration;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageRetryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** In-process Disruptor-style ring buffer messaging settings. */
@ConfigurationProperties(prefix = "nexary.messaging.disruptor")
public class DisruptorMessagingProperties {
    private boolean enabled = false;
    private int capacity = 1024;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
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
