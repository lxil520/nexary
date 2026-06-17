package org.nexary.job.execution;

/** Behavior when a scheduled execution is already too late to run. */
public enum JobMisfirePolicy {
    /** Run one execution even if the scheduled time is late. */
    FIRE_ONCE,

    /** Skip the execution when it is past the configured misfire threshold. */
    SKIP
}
