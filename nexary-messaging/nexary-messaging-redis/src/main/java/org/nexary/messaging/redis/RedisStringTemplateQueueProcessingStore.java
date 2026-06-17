package org.nexary.messaging.redis;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;

final class RedisStringTemplateQueueProcessingStore implements RedisQueueProcessingStore {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisMessagingProperties properties;

    RedisStringTemplateQueueProcessingStore(StringRedisTemplate stringRedisTemplate, RedisMessagingProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean enqueueReady(String topic, String encoded) {
        Long length = stringRedisTemplate.opsForList().leftPush(readyKey(topic), encoded);
        return length != null;
    }

    @Override
    public String moveReadyToProcessing(String topic, String consumerGroup, Duration pollTimeout) {
        String encoded = stringRedisTemplate.opsForList()
                .rightPopAndLeftPush(readyKey(topic), processingKey(topic, consumerGroup), pollTimeout);
        if (encoded != null) {
            renewLease(topic, consumerGroup, encoded);
        }
        return encoded;
    }

    @Override
    public void ack(String topic, String consumerGroup, String encoded) {
        stringRedisTemplate.opsForList().remove(processingKey(topic, consumerGroup), 1, encoded);
        stringRedisTemplate.delete(leaseKey(topic, consumerGroup, encoded));
    }

    @Override
    public void extendLease(String topic, String consumerGroup, String encoded, Duration extension) {
        stringRedisTemplate.opsForValue()
                .set(leaseKey(topic, consumerGroup, encoded), messageId(encoded), normalizeLease(extension));
    }

    @Override
    public void requeue(String topic, String consumerGroup, String encoded) {
        stringRedisTemplate.opsForList().leftPush(readyKey(topic), encoded);
        stringRedisTemplate.opsForList().remove(processingKey(topic, consumerGroup), 1, encoded);
        stringRedisTemplate.delete(leaseKey(topic, consumerGroup, encoded));
    }

    @Override
    public int recoverStale(String topic, String consumerGroup) {
        String processingKey = processingKey(topic, consumerGroup);
        List<String> processing = stringRedisTemplate.opsForList().range(processingKey, 0, -1);
        if (processing == null || processing.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (String encoded : processing) {
            Boolean leaseExists = stringRedisTemplate.hasKey(leaseKey(topic, consumerGroup, encoded));
            if (!Boolean.TRUE.equals(leaseExists)) {
                stringRedisTemplate.opsForList().leftPush(readyKey(topic), encoded);
                stringRedisTemplate.opsForList().remove(processingKey, 1, encoded);
                recovered++;
            }
        }
        return recovered;
    }

    private void renewLease(String topic, String consumerGroup, String encoded) {
        stringRedisTemplate.opsForValue()
                .set(leaseKey(topic, consumerGroup, encoded), messageId(encoded), properties.getVisibilityTimeout());
    }

    private Duration normalizeLease(Duration extension) {
        Duration lease = extension == null ? properties.getVisibilityTimeout() : extension;
        if (lease.isZero() || lease.isNegative()) {
            return properties.getVisibilityTimeout();
        }
        return lease.plus(properties.getVisibilityTimeout());
    }

    String readyKey(String topic) {
        return properties.getQueuePrefix() + topic;
    }

    String processingKey(String topic, String consumerGroup) {
        return properties.getProcessingPrefix() + topic + ":" + consumerGroup;
    }

    String leaseKey(String topic, String consumerGroup, String encoded) {
        return properties.getProcessingLeasePrefix() + topic + ":" + consumerGroup + ":" + messageId(encoded);
    }

    private String messageId(String encoded) {
        int separator = encoded.indexOf('|');
        return separator < 0 ? encoded : encoded.substring(0, separator);
    }
}
