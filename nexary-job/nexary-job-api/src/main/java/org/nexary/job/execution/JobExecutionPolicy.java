package org.nexary.job.execution;

import java.beans.ConstructorProperties;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Provider-neutral execution policy for timeout, retry, concurrency, and misfire handling. */
public final class JobExecutionPolicy {
    private final Duration timeout;
    private final int retryAttempts;
    private final Duration retryBackoff;
    private final JobConcurrencyPolicy concurrencyPolicy;
    private final JobMisfirePolicy misfirePolicy;
    private final Duration misfireThreshold;
    private final Duration lockLeaseTime;
    private final Duration startDeadline;
    private final int maxConcurrentExecutions;

    /** Creates a provider-neutral execution policy. */
    @ConstructorProperties({
            "timeout",
            "retryAttempts",
            "retryBackoff",
            "concurrencyPolicy",
            "misfirePolicy",
            "misfireThreshold",
            "lockLeaseTime"
    })
    public JobExecutionPolicy(
            Duration timeout,
            int retryAttempts,
            Duration retryBackoff,
            JobConcurrencyPolicy concurrencyPolicy,
            JobMisfirePolicy misfirePolicy,
            Duration misfireThreshold,
            Duration lockLeaseTime) {
        this(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime, null, Integer.MAX_VALUE);
    }

    /** Creates a provider-neutral execution policy with governance limits. */
    @ConstructorProperties({
            "timeout",
            "retryAttempts",
            "retryBackoff",
            "concurrencyPolicy",
            "misfirePolicy",
            "misfireThreshold",
            "lockLeaseTime",
            "startDeadline",
            "maxConcurrentExecutions"
    })
    public JobExecutionPolicy(
            Duration timeout,
            int retryAttempts,
            Duration retryBackoff,
            JobConcurrencyPolicy concurrencyPolicy,
            JobMisfirePolicy misfirePolicy,
            Duration misfireThreshold,
            Duration lockLeaseTime,
            Duration startDeadline,
            int maxConcurrentExecutions) {
        this.timeout = normalize(timeout, Duration.ofMinutes(5));
        this.retryAttempts = Math.max(0, retryAttempts);
        this.retryBackoff = normalize(retryBackoff, Duration.ZERO);
        this.concurrencyPolicy = concurrencyPolicy == null ? JobConcurrencyPolicy.ALLOW : concurrencyPolicy;
        this.misfirePolicy = misfirePolicy == null ? JobMisfirePolicy.FIRE_ONCE : misfirePolicy;
        this.misfireThreshold = normalize(misfireThreshold, Duration.ofMinutes(1));
        this.lockLeaseTime = normalize(lockLeaseTime, this.timeout.plus(this.retryBackoff.multipliedBy(Math.max(1, this.retryAttempts))));
        this.startDeadline = normalizeNullable(startDeadline);
        this.maxConcurrentExecutions = maxConcurrentExecutions <= 0 ? Integer.MAX_VALUE : maxConcurrentExecutions;
    }

    /** Returns the default v0.1 execution policy. */
    public static JobExecutionPolicy defaults() {
        return new JobExecutionPolicy(
                Duration.ofMinutes(5),
                0,
                Duration.ZERO,
                JobConcurrencyPolicy.ALLOW,
                JobMisfirePolicy.FIRE_ONCE,
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                null,
                Integer.MAX_VALUE);
    }

    /** Returns this policy with a different timeout. */
    public JobExecutionPolicy withTimeout(Duration value) {
        return new JobExecutionPolicy(value, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime, startDeadline, maxConcurrentExecutions);
    }

    /** Returns this policy with a different retry attempt count. */
    public JobExecutionPolicy withRetryAttempts(int value) {
        return new JobExecutionPolicy(timeout, value, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime, startDeadline, maxConcurrentExecutions);
    }

    /** Returns this policy with a different retry backoff. */
    public JobExecutionPolicy withRetryBackoff(Duration value) {
        return new JobExecutionPolicy(timeout, retryAttempts, value, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime, startDeadline, maxConcurrentExecutions);
    }

    /** Returns this policy with a different concurrency policy. */
    public JobExecutionPolicy withConcurrencyPolicy(JobConcurrencyPolicy value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, value, misfirePolicy, misfireThreshold, lockLeaseTime, startDeadline, maxConcurrentExecutions);
    }

    /** Returns this policy with a different misfire policy. */
    public JobExecutionPolicy withMisfirePolicy(JobMisfirePolicy value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, value, misfireThreshold, lockLeaseTime, startDeadline, maxConcurrentExecutions);
    }

    /** Returns this policy with a different misfire threshold. */
    public JobExecutionPolicy withMisfireThreshold(Duration value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, value, lockLeaseTime, startDeadline, maxConcurrentExecutions);
    }

    /** Returns this policy with a different single-instance lock lease. */
    public JobExecutionPolicy withLockLeaseTime(Duration value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, value, startDeadline, maxConcurrentExecutions);
    }

    /** Returns this policy with a different maximum delay before execution may start. */
    public JobExecutionPolicy withStartDeadline(Duration value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime, value, maxConcurrentExecutions);
    }

    /** Returns this policy with a different resource-level bulkhead limit. */
    public JobExecutionPolicy withMaxConcurrentExecutions(int value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime, startDeadline, value);
    }

    public Duration timeout() {
        return timeout;
    }

    public int retryAttempts() {
        return retryAttempts;
    }

    public Duration retryBackoff() {
        return retryBackoff;
    }

    public JobConcurrencyPolicy concurrencyPolicy() {
        return concurrencyPolicy;
    }

    public JobMisfirePolicy misfirePolicy() {
        return misfirePolicy;
    }

    public Duration misfireThreshold() {
        return misfireThreshold;
    }

    public Duration lockLeaseTime() {
        return lockLeaseTime;
    }

    /** Returns the maximum delay after scheduled time before execution may start. */
    public Optional<Duration> startDeadline() {
        return Optional.ofNullable(startDeadline);
    }

    /** Returns the resource-level bulkhead limit for concurrent executions. */
    public int maxConcurrentExecutions() {
        return maxConcurrentExecutions;
    }

    private static Duration normalize(Duration value, Duration fallback) {
        return value == null || value.isNegative() ? fallback : value;
    }

    private static Duration normalizeNullable(Duration value) {
        return value == null || value.isNegative() ? null : value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobExecutionPolicy)) {
            return false;
        }
        JobExecutionPolicy that = (JobExecutionPolicy) other;
        return retryAttempts == that.retryAttempts
                && Objects.equals(timeout, that.timeout)
                && Objects.equals(retryBackoff, that.retryBackoff)
                && concurrencyPolicy == that.concurrencyPolicy
                && misfirePolicy == that.misfirePolicy
                && Objects.equals(misfireThreshold, that.misfireThreshold)
                && Objects.equals(lockLeaseTime, that.lockLeaseTime)
                && Objects.equals(startDeadline, that.startDeadline)
                && maxConcurrentExecutions == that.maxConcurrentExecutions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime, startDeadline, maxConcurrentExecutions);
    }

    @Override
    public String toString() {
        return "JobExecutionPolicy["
                + "timeout=" + timeout
                + ", retryAttempts=" + retryAttempts
                + ", retryBackoff=" + retryBackoff
                + ", concurrencyPolicy=" + concurrencyPolicy
                + ", misfirePolicy=" + misfirePolicy
                + ", misfireThreshold=" + misfireThreshold
                + ", lockLeaseTime=" + lockLeaseTime
                + ", startDeadline=" + startDeadline
                + ", maxConcurrentExecutions=" + maxConcurrentExecutions
                + ']';
    }
}
