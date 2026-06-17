package org.nexary.job.execution;

import java.util.Optional;

/** Provider-neutral storage for completed job execution records. */
public interface JobExecutionStore {
    /** Saves one completed execution record using the store's retention policy. */
    void save(JobExecutionRecord record);

    /** Finds one execution record when it is still retained by the store. */
    Optional<JobExecutionRecord> find(JobExecutionId executionId);
}
