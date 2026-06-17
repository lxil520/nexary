package org.nexary.job;

/** Schedules and cancels jobs. */
public interface NexaryJobScheduler {
    /** Schedules a job. */
    void schedule(NexaryJob job, JobSchedule schedule);

    /** Cancels a job by name. */
    boolean cancel(String jobName);
}
