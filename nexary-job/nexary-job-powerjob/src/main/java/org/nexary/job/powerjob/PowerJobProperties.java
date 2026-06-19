package org.nexary.job.powerjob;

import java.time.Duration;
import org.nexary.job.execution.JobConcurrencyPolicy;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.execution.JobMisfirePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for Nexary's PowerJob bridge execution lifecycle. */
@ConfigurationProperties(prefix = "nexary.job.powerjob")
public class PowerJobProperties {
    /** Maximum runtime for one bridge-triggered execution attempt. */
    private Duration executionTimeout = Duration.ofMinutes(5);

    /** Number of retries after the first failed attempt. */
    private int retryAttempts = 0;

    /** Backoff between retry attempts. */
    private Duration retryBackoff = Duration.ZERO;

    /** How the bridge behaves when the same job shard is already running. */
    private JobConcurrencyPolicy concurrencyPolicy = JobConcurrencyPolicy.ALLOW;

    /** Kept provider-neutral; bridge executions normally are not cron misfires in Nexary. */
    private JobMisfirePolicy misfirePolicy = JobMisfirePolicy.FIRE_ONCE;

    /** Late-execution threshold used only when a bridge request is mapped as scheduled. */
    private Duration misfireThreshold = Duration.ofMinutes(1);

    /** Retention for in-memory execution records when no durable store is configured. */
    private Duration executionRecordRetention = Duration.ofDays(1);

    public Duration getExecutionTimeout() {
        return executionTimeout;
    }

    public void setExecutionTimeout(Duration executionTimeout) {
        this.executionTimeout = executionTimeout == null ? Duration.ofMinutes(5) : executionTimeout;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = Math.max(0, retryAttempts);
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff == null ? Duration.ZERO : retryBackoff;
    }

    public JobConcurrencyPolicy getConcurrencyPolicy() {
        return concurrencyPolicy;
    }

    public void setConcurrencyPolicy(JobConcurrencyPolicy concurrencyPolicy) {
        this.concurrencyPolicy = concurrencyPolicy == null ? JobConcurrencyPolicy.ALLOW : concurrencyPolicy;
    }

    public JobMisfirePolicy getMisfirePolicy() {
        return misfirePolicy;
    }

    public void setMisfirePolicy(JobMisfirePolicy misfirePolicy) {
        this.misfirePolicy = misfirePolicy == null ? JobMisfirePolicy.FIRE_ONCE : misfirePolicy;
    }

    public Duration getMisfireThreshold() {
        return misfireThreshold;
    }

    public void setMisfireThreshold(Duration misfireThreshold) {
        this.misfireThreshold = misfireThreshold == null ? Duration.ofMinutes(1) : misfireThreshold;
    }

    public Duration getExecutionRecordRetention() {
        return executionRecordRetention;
    }

    public void setExecutionRecordRetention(Duration executionRecordRetention) {
        this.executionRecordRetention = executionRecordRetention == null ? Duration.ofDays(1) : executionRecordRetention;
    }

    /** Builds the default execution policy for PowerJob bridge executions. */
    public JobExecutionPolicy toExecutionPolicy() {
        return new JobExecutionPolicy(
                executionTimeout,
                retryAttempts,
                retryBackoff,
                concurrencyPolicy,
                misfirePolicy,
                misfireThreshold,
                JobExecutionPolicy.defaults().lockLeaseTime());
    }
}
