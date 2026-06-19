package org.nexary.job.scheduler.boot4;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.nexary.job.JobSchedule;
import org.nexary.job.execution.JobConcurrencyPolicy;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.execution.JobMisfirePolicy;
import org.nexary.job.loadbalance.JobLoadBalanceStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the local distributed job scheduler provider. */
@ConfigurationProperties(prefix = "nexary.job.scheduler")
public class LocalJobSchedulerProperties {
    /**
     * Current worker id. When unset, the scheduler runs all shards locally unless
     * a schedule provides an explicit worker id.
     */
    private String workerId;

    /**
     * Known worker ids for local distributed scheduling. When empty, the provider
     * keeps the old single-process behavior and executes all shards locally.
     */
    private List<String> workers = new ArrayList<>();

    /** Default load-balance strategy used when a schedule does not override it. */
    private JobLoadBalanceStrategy loadBalance = JobLoadBalanceStrategy.ROUND_ROBIN;

    /** Enables cache-backed worker heartbeat and topology discovery when CacheClient is present. */
    private boolean heartbeatEnabled = true;

    /** Heartbeat write interval for the current local scheduler worker. */
    private Duration heartbeatInterval = Duration.ofSeconds(10);

    /** Time after which a worker heartbeat is considered expired. */
    private Duration heartbeatTtl = Duration.ofSeconds(30);

    /** Topology key used to isolate different applications or deployments. */
    private String topology = "default";

    /** Maximum runtime for one job execution attempt. */
    private Duration executionTimeout = Duration.ofMinutes(5);

    /** Number of retries after the first failed attempt. */
    private int retryAttempts = 0;

    /** Backoff between retry attempts. */
    private Duration retryBackoff = Duration.ZERO;

    /** How the provider behaves when the same job shard is already running. */
    private JobConcurrencyPolicy concurrencyPolicy = JobConcurrencyPolicy.ALLOW;

    /** How the provider handles late scheduled executions. */
    private JobMisfirePolicy misfirePolicy = JobMisfirePolicy.FIRE_ONCE;

    /** Scheduled executions later than this threshold are considered misfired. */
    private Duration misfireThreshold = Duration.ofMinutes(1);

    /** Lease used by the local single-instance distributed lock. */
    private Duration lockLeaseTime = Duration.ofMinutes(5);

    /** Retention for in-memory execution records when no durable store is configured. */
    private Duration executionRecordRetention = Duration.ofDays(1);

    /** Cron schedules registered from application configuration at startup. */
    private List<Schedule> schedules = new ArrayList<>();

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public List<String> getWorkers() {
        return workers;
    }

    public void setWorkers(List<String> workers) {
        this.workers = workers == null ? new ArrayList<>() : new ArrayList<>(workers);
    }

    public JobLoadBalanceStrategy getLoadBalance() {
        return loadBalance;
    }

    public void setLoadBalance(JobLoadBalanceStrategy loadBalance) {
        this.loadBalance = loadBalance == null ? JobLoadBalanceStrategy.ROUND_ROBIN : loadBalance;
    }

    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public void setHeartbeatEnabled(boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(10) : heartbeatInterval;
    }

    public Duration getHeartbeatTtl() {
        return heartbeatTtl;
    }

    public void setHeartbeatTtl(Duration heartbeatTtl) {
        this.heartbeatTtl = heartbeatTtl == null ? Duration.ofSeconds(30) : heartbeatTtl;
    }

    public String getTopology() {
        return topology;
    }

    public void setTopology(String topology) {
        this.topology = topology == null || topology.trim().isEmpty() ? "default" : topology;
    }

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

    public Duration getLockLeaseTime() {
        return lockLeaseTime;
    }

    public void setLockLeaseTime(Duration lockLeaseTime) {
        this.lockLeaseTime = lockLeaseTime == null ? Duration.ofMinutes(5) : lockLeaseTime;
    }

    public Duration getExecutionRecordRetention() {
        return executionRecordRetention;
    }

    public void setExecutionRecordRetention(Duration executionRecordRetention) {
        this.executionRecordRetention = executionRecordRetention == null ? Duration.ofDays(1) : executionRecordRetention;
    }

    public List<Schedule> getSchedules() {
        return schedules;
    }

    public void setSchedules(List<Schedule> schedules) {
        this.schedules = schedules == null ? new ArrayList<>() : new ArrayList<>(schedules);
    }

    /** Builds the default execution policy for local scheduler executions. */
    public JobExecutionPolicy toExecutionPolicy() {
        return new JobExecutionPolicy(
                executionTimeout,
                retryAttempts,
                retryBackoff,
                concurrencyPolicy,
                misfirePolicy,
                misfireThreshold,
                lockLeaseTime);
    }

    /** Builds enabled cron schedules declared under nexary.job.scheduler.schedules. */
    public List<JobSchedule> toSchedules() {
        ArrayList<JobSchedule> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            if (schedule != null && schedule.isEnabled()) {
                result.add(schedule.toJobSchedule());
            }
        }
        return result;
    }

    /** One cron schedule declared in application configuration. */
    public static class Schedule {
        private boolean enabled = true;
        private String jobName;
        private String cron;
        private boolean singleInstance = true;
        private int shardTotal = 1;
        private JobLoadBalanceStrategy loadBalance;
        private String workerId;
        private List<String> workerIds = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getJobName() {
            return jobName;
        }

        public void setJobName(String jobName) {
            this.jobName = jobName;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public boolean isSingleInstance() {
            return singleInstance;
        }

        public void setSingleInstance(boolean singleInstance) {
            this.singleInstance = singleInstance;
        }

        public int getShardTotal() {
            return shardTotal;
        }

        public void setShardTotal(int shardTotal) {
            this.shardTotal = Math.max(1, shardTotal);
        }

        public JobLoadBalanceStrategy getLoadBalance() {
            return loadBalance;
        }

        public void setLoadBalance(JobLoadBalanceStrategy loadBalance) {
            this.loadBalance = loadBalance;
        }

        public String getWorkerId() {
            return workerId;
        }

        public void setWorkerId(String workerId) {
            this.workerId = workerId;
        }

        public List<String> getWorkerIds() {
            return workerIds;
        }

        public void setWorkerIds(List<String> workerIds) {
            this.workerIds = workerIds == null ? new ArrayList<>() : new ArrayList<>(workerIds);
        }

        JobSchedule toJobSchedule() {
            return new JobSchedule(jobName, cron, singleInstance, shardTotal, loadBalance, workerId, workerIds, null);
        }
    }
}
