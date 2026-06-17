package org.nexary.job.execution;

import java.time.Duration;

/** Provider-neutral execution policy for timeout, retry, concurrency, and misfire handling. */
public record JobExecutionPolicy(
        Duration timeout,
        int retryAttempts,
        Duration retryBackoff,
        JobConcurrencyPolicy concurrencyPolicy,
        JobMisfirePolicy misfirePolicy,
        Duration misfireThreshold,
        Duration lockLeaseTime) {
    public JobExecutionPolicy {
        timeout = normalize(timeout, Duration.ofMinutes(5));
        retryAttempts = Math.max(0, retryAttempts);
        retryBackoff = normalize(retryBackoff, Duration.ZERO);
        concurrencyPolicy = concurrencyPolicy == null ? JobConcurrencyPolicy.ALLOW : concurrencyPolicy;
        misfirePolicy = misfirePolicy == null ? JobMisfirePolicy.FIRE_ONCE : misfirePolicy;
        misfireThreshold = normalize(misfireThreshold, Duration.ofMinutes(1));
        lockLeaseTime = normalize(lockLeaseTime, timeout.plus(retryBackoff.multipliedBy(Math.max(1, retryAttempts))));
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
                Duration.ofMinutes(5));
    }

    /** Returns this policy with a different timeout. */
    public JobExecutionPolicy withTimeout(Duration value) {
        return new JobExecutionPolicy(value, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime);
    }

    /** Returns this policy with a different retry attempt count. */
    public JobExecutionPolicy withRetryAttempts(int value) {
        return new JobExecutionPolicy(timeout, value, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime);
    }

    /** Returns this policy with a different retry backoff. */
    public JobExecutionPolicy withRetryBackoff(Duration value) {
        return new JobExecutionPolicy(timeout, retryAttempts, value, concurrencyPolicy, misfirePolicy, misfireThreshold, lockLeaseTime);
    }

    /** Returns this policy with a different concurrency policy. */
    public JobExecutionPolicy withConcurrencyPolicy(JobConcurrencyPolicy value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, value, misfirePolicy, misfireThreshold, lockLeaseTime);
    }

    /** Returns this policy with a different misfire policy. */
    public JobExecutionPolicy withMisfirePolicy(JobMisfirePolicy value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, value, misfireThreshold, lockLeaseTime);
    }

    /** Returns this policy with a different misfire threshold. */
    public JobExecutionPolicy withMisfireThreshold(Duration value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, value, lockLeaseTime);
    }

    /** Returns this policy with a different single-instance lock lease. */
    public JobExecutionPolicy withLockLeaseTime(Duration value) {
        return new JobExecutionPolicy(timeout, retryAttempts, retryBackoff, concurrencyPolicy, misfirePolicy, misfireThreshold, value);
    }

    private static Duration normalize(Duration value, Duration fallback) {
        return value == null || value.isNegative() ? fallback : value;
    }
}
