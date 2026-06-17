package org.nexary.job;

/** Executable job unit. */
public interface NexaryJob {
    /** Stable job name. */
    String name();

    /** Executes the job. */
    JobResult execute(JobContext context) throws Exception;
}
