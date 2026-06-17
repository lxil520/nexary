package org.nexary.messaging.redis.boot2;

import java.time.Duration;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageRetryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring Boot 2 Redis queue messaging adapter settings. */
@ConfigurationProperties(prefix = "nexary.messaging.redis")
public class RedisBoot2MessagingProperties {
    private boolean enabled = false;
    private String queuePrefix = "nexary:mq:";
    private String processingPrefix = "nexary:mq:processing:";
    private String processingLeasePrefix = "nexary:mq:processing:lease:";
    private String deduplicationPrefix = "nexary:mq:dedup:";
    private Duration pollTimeout = Duration.ofSeconds(1);
    private Duration visibilityTimeout = Duration.ofSeconds(30);
    private Duration processingRecoveryInterval = Duration.ofSeconds(5);
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

    public String getQueuePrefix() {
        return queuePrefix;
    }

    public void setQueuePrefix(String queuePrefix) {
        this.queuePrefix = queuePrefix;
    }

    public String getProcessingPrefix() {
        return processingPrefix;
    }

    public void setProcessingPrefix(String processingPrefix) {
        this.processingPrefix = processingPrefix;
    }

    public String getProcessingLeasePrefix() {
        return processingLeasePrefix;
    }

    public void setProcessingLeasePrefix(String processingLeasePrefix) {
        this.processingLeasePrefix = processingLeasePrefix;
    }

    public String getDeduplicationPrefix() {
        return deduplicationPrefix;
    }

    public void setDeduplicationPrefix(String deduplicationPrefix) {
        this.deduplicationPrefix = deduplicationPrefix;
    }

    public Duration getPollTimeout() {
        return pollTimeout;
    }

    public void setPollTimeout(Duration pollTimeout) {
        this.pollTimeout = pollTimeout;
    }

    public Duration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setVisibilityTimeout(Duration visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }

    public Duration getProcessingRecoveryInterval() {
        return processingRecoveryInterval;
    }

    public void setProcessingRecoveryInterval(Duration processingRecoveryInterval) {
        this.processingRecoveryInterval = processingRecoveryInterval;
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

    /** Builds the provider-neutral retry policy used by the Boot2 Redis queue. */
    public MessageRetryPolicy toRetryPolicy() {
        return new MessageRetryPolicy(
                retryMaxAttempts,
                retryInitialDelay,
                retryBackoffStrategy,
                retryBackoffMultiplier,
                retryMaxBackoff);
    }
}
