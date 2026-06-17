package org.nexary.job.execution;

/** Concurrency behavior when another execution of the same shard is already running. */
public enum JobConcurrencyPolicy {
    /** Allow overlapping executions. */
    ALLOW,

    /** Skip a new execution if the same job shard is already running in this JVM. */
    SKIP_IF_RUNNING
}
