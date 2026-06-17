package org.nexary.job;

import java.time.Instant;
import org.nexary.core.context.TrafficTag;

/** Runtime context passed to a job execution. */
public record JobContext(String jobName, Instant scheduledAt, int shardIndex, int shardTotal, TrafficTag trafficTag) {
    public JobContext {
        scheduledAt = scheduledAt == null ? Instant.now() : scheduledAt;
        shardTotal = Math.max(1, shardTotal);
        shardIndex = Math.max(0, shardIndex);
        trafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
    }
}
