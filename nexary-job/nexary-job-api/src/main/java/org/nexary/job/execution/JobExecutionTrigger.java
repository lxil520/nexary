package org.nexary.job.execution;

/** Source that requested a job execution. */
public enum JobExecutionTrigger {
    /** Direct programmatic trigger through NexaryJobOperations. */
    DIRECT,

    /** Local cron scheduler trigger. */
    SCHEDULED,

    /** External platform bridge trigger, such as XXL-JOB. */
    BRIDGE
}
