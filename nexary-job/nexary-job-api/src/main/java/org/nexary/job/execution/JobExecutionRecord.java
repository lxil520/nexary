package org.nexary.job.execution;

import java.beans.ConstructorProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.internal.JobCompatibilityCollections;

/** Immutable value for one completed job execution. */
public final class JobExecutionRecord {
    private final JobExecutionId executionId;
    private final JobExecutionTrigger trigger;
    private final JobContext context;
    private final JobExecutionStatus status;
    private final JobResult result;
    private final int attempts;
    private final Instant startedAt;
    private final Instant endedAt;
    private final Duration duration;
    private final String message;
    private final String errorMessage;
    private final Map<String, String> providerMetadata;

    /** Creates an immutable value for one completed job execution. */
    public JobExecutionRecord(
            JobExecutionId executionId,
            JobExecutionTrigger trigger,
            JobContext context,
            JobExecutionStatus status,
            JobResult result,
            int attempts,
            Instant startedAt,
            Instant endedAt,
            Duration duration,
            String message,
            String errorMessage) {
        this(
                executionId,
                trigger,
                context,
                status,
                result,
                attempts,
                startedAt,
                endedAt,
                duration,
                message,
                errorMessage,
                null);
    }

    /** Creates an immutable value for one completed job execution with provider metadata. */
    @ConstructorProperties({
            "executionId",
            "trigger",
            "context",
            "status",
            "result",
            "attempts",
            "startedAt",
            "endedAt",
            "duration",
            "message",
            "errorMessage",
            "providerMetadata"
    })
    public JobExecutionRecord(
            JobExecutionId executionId,
            JobExecutionTrigger trigger,
            JobContext context,
            JobExecutionStatus status,
            JobResult result,
            int attempts,
            Instant startedAt,
            Instant endedAt,
            Duration duration,
            String message,
            String errorMessage,
            Map<String, String> providerMetadata) {
        this.executionId = executionId;
        this.trigger = trigger;
        this.context = context;
        this.status = status;
        this.result = result;
        this.attempts = attempts;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.duration = duration;
        this.message = message;
        this.errorMessage = errorMessage;
        this.providerMetadata = JobCompatibilityCollections.copyMap(providerMetadata);
    }

    public JobExecutionId executionId() {
        return executionId;
    }

    public JobExecutionTrigger trigger() {
        return trigger;
    }

    public JobContext context() {
        return context;
    }

    public JobExecutionStatus status() {
        return status;
    }

    public JobResult result() {
        return result;
    }

    public int attempts() {
        return attempts;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Duration duration() {
        return duration;
    }

    public String message() {
        return message;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Map<String, String> providerMetadata() {
        return providerMetadata;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobExecutionRecord)) {
            return false;
        }
        JobExecutionRecord that = (JobExecutionRecord) other;
        return attempts == that.attempts
                && Objects.equals(executionId, that.executionId)
                && trigger == that.trigger
                && Objects.equals(context, that.context)
                && status == that.status
                && Objects.equals(result, that.result)
                && Objects.equals(startedAt, that.startedAt)
                && Objects.equals(endedAt, that.endedAt)
                && Objects.equals(duration, that.duration)
                && Objects.equals(message, that.message)
                && Objects.equals(errorMessage, that.errorMessage)
                && Objects.equals(providerMetadata, that.providerMetadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, trigger, context, status, result, attempts, startedAt, endedAt, duration, message, errorMessage, providerMetadata);
    }

    @Override
    public String toString() {
        return "JobExecutionRecord["
                + "executionId=" + executionId
                + ", trigger=" + trigger
                + ", context=" + context
                + ", status=" + status
                + ", result=" + result
                + ", attempts=" + attempts
                + ", startedAt=" + startedAt
                + ", endedAt=" + endedAt
                + ", duration=" + duration
                + ", message=" + message
                + ", errorMessage=" + errorMessage
                + ", providerMetadata=" + providerMetadata
                + ']';
    }
}
