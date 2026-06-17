package org.nexary.job;

import java.beans.ConstructorProperties;
import java.util.Objects;

/** Job execution result. */
public final class JobResult {
    private final JobStatus status;
    private final String message;

    /** Creates a job execution result. */
    @ConstructorProperties({"status", "message"})
    public JobResult(JobStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /** Successful result. */
    public static JobResult success() {
        return new JobResult(JobStatus.SUCCESS, "");
    }

    /** Failed result. */
    public static JobResult failed(String message) {
        return new JobResult(JobStatus.FAILED, message);
    }

    public JobStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobResult)) {
            return false;
        }
        JobResult that = (JobResult) other;
        return status == that.status && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, message);
    }

    @Override
    public String toString() {
        return "JobResult[status=" + status + ", message=" + message + ']';
    }

    /** Job status. */
    public enum JobStatus { SUCCESS, FAILED, SKIPPED }
}
