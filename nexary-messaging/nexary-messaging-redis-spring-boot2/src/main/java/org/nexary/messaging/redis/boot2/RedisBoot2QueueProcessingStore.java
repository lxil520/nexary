package org.nexary.messaging.redis.boot2;

import java.time.Duration;

interface RedisBoot2QueueProcessingStore {
    boolean enqueueReady(String topic, String encoded);

    String moveReadyToProcessing(String topic, String consumerGroup, Duration pollTimeout);

    void ack(String topic, String consumerGroup, String encoded);

    void extendLease(String topic, String consumerGroup, String encoded, Duration extension);

    void requeue(String topic, String consumerGroup, String encoded);

    int recoverStale(String topic, String consumerGroup);
}
