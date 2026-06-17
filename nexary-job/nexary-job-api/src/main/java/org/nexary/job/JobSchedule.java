package org.nexary.job;

import java.beans.ConstructorProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.loadbalance.JobLoadBalanceStrategy;

/**
 * Cron-based job schedule definition.
 *
 * <p>If {@code loadBalance}, {@code workerId}, or {@code workerIds} are not set,
 * the active scheduler provider may use its configured defaults.
 */
public final class JobSchedule {
    private final String jobName;
    private final String cron;
    private final boolean singleInstance;
    private final int shardTotal;
    private final JobLoadBalanceStrategy loadBalance;
    private final String workerId;
    private final List<String> workerIds;
    private final JobExecutionPolicy executionPolicy;

    /** Creates a cron-based job schedule definition. */
    @ConstructorProperties({
            "jobName",
            "cron",
            "singleInstance",
            "shardTotal",
            "loadBalance",
            "workerId",
            "workerIds",
            "executionPolicy"
    })
    public JobSchedule(
            String jobName,
            String cron,
            boolean singleInstance,
            int shardTotal,
            JobLoadBalanceStrategy loadBalance,
            String workerId,
            List<String> workerIds,
            JobExecutionPolicy executionPolicy) {
        this.jobName = Objects.requireNonNull(jobName, "jobName");
        this.cron = Objects.requireNonNull(cron, "cron");
        this.singleInstance = singleInstance;
        this.shardTotal = Math.max(1, shardTotal);
        this.loadBalance = loadBalance;
        this.workerId = workerId == null || workerId.trim().isEmpty() ? null : workerId.trim();
        this.workerIds = normalizeWorkerIds(workerIds);
        this.executionPolicy = executionPolicy;
    }

    /** Creates a schedule without explicit distributed worker assignment. */
    public JobSchedule(String jobName, String cron, boolean singleInstance, int shardTotal) {
        this(jobName, cron, singleInstance, shardTotal, null, null, Collections.emptyList(), null);
    }

    /** Creates a single-instance schedule. */
    public static JobSchedule single(String jobName, String cron) {
        return new JobSchedule(jobName, cron, true, 1);
    }

    /** Creates a sharded distributed schedule assigned by the given load-balance strategy. */
    public static JobSchedule sharded(
            String jobName,
            String cron,
            int shardTotal,
            JobLoadBalanceStrategy loadBalance,
            String workerId,
            List<String> workerIds) {
        return new JobSchedule(jobName, cron, false, shardTotal, loadBalance, workerId, workerIds, null);
    }

    /** Returns this schedule with an explicit execution policy. */
    public JobSchedule withExecutionPolicy(JobExecutionPolicy policy) {
        return new JobSchedule(jobName, cron, singleInstance, shardTotal, loadBalance, workerId, workerIds, policy);
    }

    public String jobName() {
        return jobName;
    }

    public String cron() {
        return cron;
    }

    public boolean singleInstance() {
        return singleInstance;
    }

    public int shardTotal() {
        return shardTotal;
    }

    public JobLoadBalanceStrategy loadBalance() {
        return loadBalance;
    }

    public String workerId() {
        return workerId;
    }

    public List<String> workerIds() {
        return workerIds;
    }

    public JobExecutionPolicy executionPolicy() {
        return executionPolicy;
    }

    private static List<String> normalizeWorkerIds(List<String> workerIds) {
        if (workerIds == null) {
            return Collections.emptyList();
        }
        ArrayList<String> normalizedWorkerIds = workerIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
        return Collections.unmodifiableList(normalizedWorkerIds);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobSchedule)) {
            return false;
        }
        JobSchedule that = (JobSchedule) other;
        return singleInstance == that.singleInstance
                && shardTotal == that.shardTotal
                && Objects.equals(jobName, that.jobName)
                && Objects.equals(cron, that.cron)
                && loadBalance == that.loadBalance
                && Objects.equals(workerId, that.workerId)
                && Objects.equals(workerIds, that.workerIds)
                && Objects.equals(executionPolicy, that.executionPolicy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobName, cron, singleInstance, shardTotal, loadBalance, workerId, workerIds, executionPolicy);
    }

    @Override
    public String toString() {
        return "JobSchedule["
                + "jobName=" + jobName
                + ", cron=" + cron
                + ", singleInstance=" + singleInstance
                + ", shardTotal=" + shardTotal
                + ", loadBalance=" + loadBalance
                + ", workerId=" + workerId
                + ", workerIds=" + workerIds
                + ", executionPolicy=" + executionPolicy
                + ']';
    }
}
