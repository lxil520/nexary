package org.nexary.job.execution;

import java.beans.ConstructorProperties;
import java.time.Instant;
import java.util.Objects;
import org.nexary.job.JobContext;

/** Input to the shared job execution pipeline. */
public final class JobExecutionRequest {
    private final JobExecutionId executionId;
    private final JobExecutionTrigger trigger;
    private final JobContext context;
    private final JobExecutionPolicy policy;

    /** Creates input to the shared job execution pipeline. */
    @ConstructorProperties({"executionId", "trigger", "context", "policy"})
    public JobExecutionRequest(
            JobExecutionId executionId,
            JobExecutionTrigger trigger,
            JobContext context,
            JobExecutionPolicy policy) {
        this.executionId = executionId == null ? JobExecutionId.generate() : executionId;
        this.trigger = trigger == null ? JobExecutionTrigger.DIRECT : trigger;
        this.context = context == null ? new JobContext("unknown", Instant.now(), 0, 1, null) : context;
        this.policy = policy == null ? JobExecutionPolicy.defaults() : policy;
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

    public JobExecutionPolicy policy() {
        return policy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobExecutionRequest)) {
            return false;
        }
        JobExecutionRequest that = (JobExecutionRequest) other;
        return Objects.equals(executionId, that.executionId)
                && trigger == that.trigger
                && Objects.equals(context, that.context)
                && Objects.equals(policy, that.policy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, trigger, context, policy);
    }

    @Override
    public String toString() {
        return "JobExecutionRequest["
                + "executionId=" + executionId
                + ", trigger=" + trigger
                + ", context=" + context
                + ", policy=" + policy
                + ']';
    }
}
