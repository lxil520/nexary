package org.nexary.job;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.Objects;
import org.nexary.core.context.TrafficTag;

/** Runtime context passed to a job execution. */
public final class JobContext {
    private final String jobName;
    private final Instant scheduledAt;
    private final int shardIndex;
    private final int shardTotal;
    private final TrafficTag trafficTag;

    /** Creates a runtime context passed to a job execution. */
    @ConstructorProperties({"jobName", "scheduledAt", "shardIndex", "shardTotal", "trafficTag"})
    public JobContext(String jobName, Instant scheduledAt, int shardIndex, int shardTotal, TrafficTag trafficTag) {
        this.jobName = jobName;
        this.scheduledAt = scheduledAt == null ? Instant.now() : scheduledAt;
        this.shardIndex = Math.max(0, shardIndex);
        this.shardTotal = Math.max(1, shardTotal);
        this.trafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
    }

    public String jobName() {
        return jobName;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }

    public int shardIndex() {
        return shardIndex;
    }

    public int shardTotal() {
        return shardTotal;
    }

    public TrafficTag trafficTag() {
        return trafficTag;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobContext)) {
            return false;
        }
        JobContext that = (JobContext) other;
        return shardIndex == that.shardIndex
                && shardTotal == that.shardTotal
                && Objects.equals(jobName, that.jobName)
                && Objects.equals(scheduledAt, that.scheduledAt)
                && Objects.equals(trafficTag, that.trafficTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobName, scheduledAt, shardIndex, shardTotal, trafficTag);
    }

    @Override
    public String toString() {
        return "JobContext["
                + "jobName=" + jobName
                + ", scheduledAt=" + scheduledAt
                + ", shardIndex=" + shardIndex
                + ", shardTotal=" + shardTotal
                + ", trafficTag=" + trafficTag
                + ']';
    }
}
