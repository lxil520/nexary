package org.nexary.job.loadbalance;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.CRC32;

/** Factory for Nexary built-in job load balancers. */
public final class JobLoadBalancers {
    private JobLoadBalancers() {
    }

    /** Returns a built-in load balancer for the strategy. */
    public static JobLoadBalancer create(JobLoadBalanceStrategy strategy) {
        return switch (strategy == null ? JobLoadBalanceStrategy.ROUND_ROBIN : strategy) {
            case ROUND_ROBIN -> new RoundRobinJobLoadBalancer();
            case RANDOM -> new RandomJobLoadBalancer();
            case CONSISTENT_HASH -> new ConsistentHashJobLoadBalancer();
            case LEAST_ACTIVE -> new LeastActiveJobLoadBalancer();
            case FIRST_AVAILABLE -> new FirstAvailableJobLoadBalancer();
        };
    }

    private abstract static class AbstractJobLoadBalancer implements JobLoadBalancer {
        JobWorker requireWorker(List<JobWorker> workers) {
            Objects.requireNonNull(workers, "workers");
            if (workers.isEmpty()) {
                throw new IllegalArgumentException("workers must not be empty");
            }
            return null;
        }
    }

    private static final class RoundRobinJobLoadBalancer extends AbstractJobLoadBalancer {
        @Override
        public JobLoadBalanceStrategy strategy() {
            return JobLoadBalanceStrategy.ROUND_ROBIN;
        }

        @Override
        public JobWorker select(String jobName, int shardIndex, List<JobWorker> workers) {
            requireWorker(workers);
            int index = Math.floorMod(shardIndex, workers.size());
            return workers.get(index);
        }
    }

    private static final class RandomJobLoadBalancer extends AbstractJobLoadBalancer {
        @Override
        public JobLoadBalanceStrategy strategy() {
            return JobLoadBalanceStrategy.RANDOM;
        }

        @Override
        public JobWorker select(String jobName, int shardIndex, List<JobWorker> workers) {
            requireWorker(workers);
            return workers.get(ThreadLocalRandom.current().nextInt(workers.size()));
        }
    }

    private static final class ConsistentHashJobLoadBalancer extends AbstractJobLoadBalancer {
        @Override
        public JobLoadBalanceStrategy strategy() {
            return JobLoadBalanceStrategy.CONSISTENT_HASH;
        }

        @Override
        public JobWorker select(String jobName, int shardIndex, List<JobWorker> workers) {
            requireWorker(workers);
            long hash = hash(jobName + ":" + shardIndex);
            long bestDistance = Long.MAX_VALUE;
            JobWorker selected = workers.get(0);
            for (JobWorker worker : workers) {
                for (int replica = 0; replica < worker.weight(); replica++) {
                    long nodeHash = hash(worker.id() + "#" + replica);
                    long distance = Math.floorMod(nodeHash - hash, Integer.MAX_VALUE);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        selected = worker;
                    }
                }
            }
            return selected;
        }
    }

    private static final class LeastActiveJobLoadBalancer extends AbstractJobLoadBalancer {
        @Override
        public JobLoadBalanceStrategy strategy() {
            return JobLoadBalanceStrategy.LEAST_ACTIVE;
        }

        @Override
        public JobWorker select(String jobName, int shardIndex, List<JobWorker> workers) {
            requireWorker(workers);
            return workers.stream()
                    .min(Comparator.comparingInt(JobWorker::activeCount)
                            .thenComparing(worker -> hash(jobName + ":" + shardIndex + ":" + worker.id())))
                    .orElseThrow();
        }
    }

    private static final class FirstAvailableJobLoadBalancer extends AbstractJobLoadBalancer {
        @Override
        public JobLoadBalanceStrategy strategy() {
            return JobLoadBalanceStrategy.FIRST_AVAILABLE;
        }

        @Override
        public JobWorker select(String jobName, int shardIndex, List<JobWorker> workers) {
            requireWorker(workers);
            return workers.get(0);
        }
    }

    private static long hash(String value) {
        CRC32 crc32 = new CRC32();
        crc32.update(value.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }
}
