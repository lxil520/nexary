package org.nexary.job.loadbalance;

import java.util.Objects;

/** A candidate worker that can execute a local scheduled job shard. */
public record JobWorker(String id, int activeCount, int weight) {
    public JobWorker {
        Objects.requireNonNull(id, "id");
        if (id.trim().isEmpty()) {
            throw new IllegalArgumentException("worker id must not be blank");
        }
        activeCount = Math.max(0, activeCount);
        weight = Math.max(1, weight);
    }

    /** Creates a worker with no active executions and weight 1. */
    public static JobWorker of(String id) {
        return new JobWorker(id, 0, 1);
    }
}
