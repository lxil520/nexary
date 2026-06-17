package org.nexary.messaging.rocketmq;

import java.time.Duration;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageRetryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** RocketMQ messaging adapter settings. */
@ConfigurationProperties(prefix = "nexary.messaging.rocketmq")
public class RocketMqMessagingProperties {
    private boolean enabled = true;
    private String namesrvAddr = "127.0.0.1:9876";
    private String producerGroup = "nexary-messaging-producer";
    private boolean autoCreateTopic = false;
    private int topicQueueNums = 1;
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

    public String getNamesrvAddr() {
        return namesrvAddr;
    }

    public void setNamesrvAddr(String namesrvAddr) {
        this.namesrvAddr = namesrvAddr;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public boolean isAutoCreateTopic() {
        return autoCreateTopic;
    }

    public void setAutoCreateTopic(boolean autoCreateTopic) {
        this.autoCreateTopic = autoCreateTopic;
    }

    public int getTopicQueueNums() {
        return topicQueueNums;
    }

    public void setTopicQueueNums(int topicQueueNums) {
        this.topicQueueNums = topicQueueNums;
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
