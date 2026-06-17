package org.nexary.job.execution;

import java.util.Objects;
import java.util.UUID;

/** Stable identity for one job execution attempt group. */
public record JobExecutionId(String value) {
    public JobExecutionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("execution id must not be blank");
        }
    }

    /** Creates a random execution id. */
    public static JobExecutionId generate() {
        return new JobExecutionId(UUID.randomUUID().toString());
    }
}
