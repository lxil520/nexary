package org.nexary.job.loadbalance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobLoadBalancersTest {
    private final List<JobWorker> workers = List.of(JobWorker.of("node-a"), JobWorker.of("node-b"), JobWorker.of("node-c"));

    @Test
    void roundRobinAssignsShardsByPosition() {
        JobLoadBalancer balancer = JobLoadBalancers.create(JobLoadBalanceStrategy.ROUND_ROBIN);

        assertThat(balancer.select("sample-job", 0, workers).id()).isEqualTo("node-a");
        assertThat(balancer.select("sample-job", 1, workers).id()).isEqualTo("node-b");
        assertThat(balancer.select("sample-job", 2, workers).id()).isEqualTo("node-c");
        assertThat(balancer.select("sample-job", 3, workers).id()).isEqualTo("node-a");
    }

    @Test
    void consistentHashIsStableForSameShard() {
        JobLoadBalancer balancer = JobLoadBalancers.create(JobLoadBalanceStrategy.CONSISTENT_HASH);

        String first = balancer.select("sample-job", 7, workers).id();
        String second = balancer.select("sample-job", 7, workers).id();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void leastActivePrefersLowestActiveWorker() {
        JobLoadBalancer balancer = JobLoadBalancers.create(JobLoadBalanceStrategy.LEAST_ACTIVE);
        List<JobWorker> candidates = List.of(new JobWorker("node-a", 3, 1), new JobWorker("node-b", 1, 1));

        assertThat(balancer.select("sample-job", 0, candidates).id()).isEqualTo("node-b");
    }
}
