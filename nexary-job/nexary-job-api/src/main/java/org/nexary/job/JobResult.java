package org.nexary.job;

/** Job execution result. */
public record JobResult(JobStatus status, String message) {
    /** Successful result. */
    public static JobResult success() {
        return new JobResult(JobStatus.SUCCESS, "");
    }

    /** Failed result. */
    public static JobResult failed(String message) {
        return new JobResult(JobStatus.FAILED, message);
    }

    /** Job status. */
    public enum JobStatus { SUCCESS, FAILED, SKIPPED }
}
