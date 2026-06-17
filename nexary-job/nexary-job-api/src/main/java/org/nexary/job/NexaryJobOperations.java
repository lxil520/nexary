package org.nexary.job;

import java.util.Optional;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;

/** Provider-neutral operations for triggering, scheduling, and cancelling jobs. */
public interface NexaryJobOperations {
    /** Returns the active job provider name selected by configuration or dependency. */
    String provider();

    /** Returns whether the active provider can register local schedules. */
    boolean supportsScheduling();

    /** Triggers a job with explicit shard metadata. */
    JobResult trigger(String jobName, int shardIndex, int shardTotal);

    /** Triggers a job with explicit shard metadata and returns execution metadata. */
    JobExecutionRecord triggerExecution(String jobName, int shardIndex, int shardTotal);

    /** Returns a completed execution record when it is still retained by the provider. */
    Optional<JobExecutionRecord> execution(JobExecutionId executionId);

    /** Registers a schedule when the active provider supports scheduling. */
    void schedule(JobSchedule schedule);

    /** Cancels a schedule when the active provider supports cancellation. */
    boolean cancel(String jobName);

    /** Attempts to cancel a running execution. v0.1 providers return false. */
    default boolean cancelExecution(JobExecutionId executionId) {
        return false;
    }
}
