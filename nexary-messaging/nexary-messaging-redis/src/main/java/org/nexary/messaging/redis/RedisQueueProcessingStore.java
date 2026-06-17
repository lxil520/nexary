package org.nexary.messaging.redis;

import java.time.Duration;

interface RedisQueueProcessingStore {
    boolean enqueueReady(String topic, String encoded);

    String moveReadyToProcessing(String topic, String consumerGroup, Duration pollTimeout);

    void ack(String topic, String consumerGroup, String encoded);

    void extendLease(String topic, String consumerGroup, String encoded, Duration extension);

    void requeue(String topic, String consumerGroup, String encoded);

    int recoverStale(String topic, String consumerGroup);
}
