package org.nexary.job.loadbalance;

import java.util.List;

/** Selects the worker that should execute a scheduled job shard. */
public interface JobLoadBalancer {
    /** Returns the strategy implemented by this load balancer. */
    JobLoadBalanceStrategy strategy();

    /** Selects one worker for a job shard from the available candidates. */
    JobWorker select(String jobName, int shardIndex, List<JobWorker> workers);
}
