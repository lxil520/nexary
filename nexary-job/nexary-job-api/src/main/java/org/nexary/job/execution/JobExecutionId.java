package org.nexary.job.execution;

import java.beans.ConstructorProperties;
import java.util.Objects;
import java.util.UUID;

/** Stable identity for one job execution attempt group. */
public final class JobExecutionId {
    private final String value;

    /** Creates a stable identity for one job execution attempt group. */
    @ConstructorProperties({"value"})
    public JobExecutionId(String value) {
        this.value = Objects.requireNonNull(value, "value");
        if (this.value.trim().isEmpty()) {
            throw new IllegalArgumentException("execution id must not be blank");
        }
    }

    /** Creates a random execution id. */
    public static JobExecutionId generate() {
        return new JobExecutionId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobExecutionId)) {
            return false;
        }
        JobExecutionId that = (JobExecutionId) other;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "JobExecutionId[value=" + value + ']';
    }
}
