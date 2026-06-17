package org.nexary.job;

import org.nexary.job.execution.JobExecutionRecord;

/** Observes job execution outcomes. */
public interface JobExecutionListener {
    /** Called after each job execution. */
    void afterExecution(JobContext context, JobResult result, Throwable error);

    /** Called after each job execution with provider-neutral execution metadata. */
    default void afterExecution(JobExecutionRecord record) {
    }
}
