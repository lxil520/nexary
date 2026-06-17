package org.nexary.job.loadbalance;

import java.beans.ConstructorProperties;
import java.util.Objects;

/** A candidate worker that can execute a local scheduled job shard. */
public final class JobWorker {
    private final String id;
    private final int activeCount;
    private final int weight;

    /** Creates a candidate worker that can execute a local scheduled job shard. */
    @ConstructorProperties({"id", "activeCount", "weight"})
    public JobWorker(String id, int activeCount, int weight) {
        this.id = Objects.requireNonNull(id, "id");
        if (this.id.trim().isEmpty()) {
            throw new IllegalArgumentException("worker id must not be blank");
        }
        this.activeCount = Math.max(0, activeCount);
        this.weight = Math.max(1, weight);
    }

    /** Creates a worker with no active executions and weight 1. */
    public static JobWorker of(String id) {
        return new JobWorker(id, 0, 1);
    }

    public String id() {
        return id;
    }

    public int activeCount() {
        return activeCount;
    }

    public int weight() {
        return weight;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobWorker)) {
            return false;
        }
        JobWorker that = (JobWorker) other;
        return activeCount == that.activeCount && weight == that.weight && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, activeCount, weight);
    }

    @Override
    public String toString() {
        return "JobWorker[id=" + id + ", activeCount=" + activeCount + ", weight=" + weight + ']';
    }
}
