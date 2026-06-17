package org.nexary.job.execution;

import java.time.Instant;
import org.nexary.job.JobContext;

/** Input to the shared job execution pipeline. */
public record JobExecutionRequest(
        JobExecutionId executionId,
        JobExecutionTrigger trigger,
        JobContext context,
        JobExecutionPolicy policy) {
    public JobExecutionRequest {
        executionId = executionId == null ? JobExecutionId.generate() : executionId;
        trigger = trigger == null ? JobExecutionTrigger.DIRECT : trigger;
        context = context == null ? new JobContext("unknown", Instant.now(), 0, 1, null) : context;
        policy = policy == null ? JobExecutionPolicy.defaults() : policy;
    }
}
