package org.nexary.job.execution;

import java.time.Duration;
import java.time.Instant;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;

/** Immutable record for one completed job execution. */
public record JobExecutionRecord(
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
}
