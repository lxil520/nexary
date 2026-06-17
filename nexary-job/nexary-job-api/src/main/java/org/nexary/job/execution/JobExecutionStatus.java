package org.nexary.job.execution;

/** Final status of a job execution record. */
public enum JobExecutionStatus {
    /** The job returned a successful result. */
    SUCCESS,

    /** The job returned failure or threw an exception after all retries. */
    FAILED,

    /** The job was intentionally skipped by policy. */
    SKIPPED,

    /** The job exceeded the configured timeout. */
    TIMEOUT,

    /** Running cancellation is not supported in v0.1; kept for future record compatibility. */
    CANCELLED
}
