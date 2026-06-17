package org.nexary.job.scheduler.boot2;

import java.util.List;
import org.nexary.job.JobSchedule;

/** Resolves local scheduler workers for distributed shard assignment. */
interface LocalJobWorkerRegistry {
    /** Returns the current worker id, or {@code null} when distributed mode is not configured. */
    String currentWorkerId(JobSchedule schedule);

    /** Returns active worker ids for the schedule. */
    List<String> workerIds(JobSchedule schedule);

    /** Writes a heartbeat for the current worker when supported. */
    void heartbeat();

    /** Returns whether this registry can publish useful background heartbeats. */
    boolean supportsHeartbeat();
}
