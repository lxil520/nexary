package org.nexary.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Collections;
import java.util.stream.Collectors;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.loadbalance.JobLoadBalanceStrategy;

/**
 * Cron-based job schedule definition.
 *
 * <p>If {@code loadBalance}, {@code workerId}, or {@code workerIds} are not set,
 * the active scheduler provider may use its configured defaults.
 */
public record JobSchedule(
        String jobName,
        String cron,
        boolean singleInstance,
        int shardTotal,
        JobLoadBalanceStrategy loadBalance,
        String workerId,
        List<String> workerIds,
        JobExecutionPolicy executionPolicy) {
    public JobSchedule {
        Objects.requireNonNull(jobName, "jobName");
        Objects.requireNonNull(cron, "cron");
        shardTotal = Math.max(1, shardTotal);
        if (workerIds == null) {
            workerIds = Collections.emptyList();
        } else {
            ArrayList<String> normalizedWorkerIds = workerIds.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));
            workerIds = Collections.unmodifiableList(normalizedWorkerIds);
        }
        workerId = workerId == null || workerId.trim().isEmpty() ? null : workerId.trim();
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
}
