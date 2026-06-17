package org.nexary.job.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobScheduler;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.execution.JobExecutionRequest;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.execution.JobObservationSupport;
import org.nexary.job.loadbalance.JobLoadBalanceStrategy;
import org.nexary.job.loadbalance.JobLoadBalancer;
import org.nexary.job.loadbalance.JobLoadBalancers;
import org.nexary.job.loadbalance.JobWorker;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

/** Local TaskScheduler-backed job scheduler. */
public class LocalNexaryJobScheduler implements NexaryJobScheduler {
    private final TaskScheduler taskScheduler;
    private final Optional<CacheClient> cacheClient;
    private final JobExecutionRunner executionRunner;
    private final LocalJobSchedulerProperties properties;
    private final LocalJobWorkerRegistry workerRegistry;
    private final NexaryObservationPublisher observationPublisher;
    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> activeCounts = new ConcurrentHashMap<>();

    public LocalNexaryJobScheduler(
            TaskScheduler taskScheduler,
            Optional<CacheClient> cacheClient,
            JobExecutionRunner executionRunner,
            LocalJobSchedulerProperties properties,
            LocalJobWorkerRegistry workerRegistry) {
        this(taskScheduler, cacheClient, executionRunner, properties, workerRegistry, NexaryObservationPublisher.noop());
    }

    public LocalNexaryJobScheduler(
            TaskScheduler taskScheduler,
            Optional<CacheClient> cacheClient,
            JobExecutionRunner executionRunner,
            LocalJobSchedulerProperties properties,
            LocalJobWorkerRegistry workerRegistry,
            NexaryObservationPublisher observationPublisher) {
        this.taskScheduler = taskScheduler;
        this.cacheClient = cacheClient;
        this.executionRunner = executionRunner;
        this.properties = properties == null ? new LocalJobSchedulerProperties() : properties;
        this.workerRegistry = workerRegistry == null
                ? new CacheBackedLocalJobWorkerRegistry(Optional.empty(), this.properties)
                : workerRegistry;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public void schedule(NexaryJob job, JobSchedule schedule) {
        ScheduledFuture<?> future = taskScheduler.schedule(() -> runJob(job, schedule), new CronTrigger(schedule.cron()));
        if (future != null) {
            futures.put(schedule.jobName(), future);
        }
    }

    @Override
    public boolean cancel(String jobName) {
        ScheduledFuture<?> future = futures.remove(jobName);
        return future != null && future.cancel(false);
    }

    private void runJob(NexaryJob job, JobSchedule schedule) {
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_SCHEDULER_RUN,
                "local",
                JobExecutionTrigger.SCHEDULED,
                "started",
                Map.of("shard_presence", schedule.shardTotal() > 1 ? "true" : "false"),
                null);
        JobExecutionPolicy policy = effectivePolicy(schedule);
        if (schedule.singleInstance() && cacheClient.isPresent()) {
            CacheKey lockKey = CacheKey.of("job", schedule.jobName());
            Optional<LockHandle> lock = cacheClient.get().tryLock(lockKey, Duration.ZERO, policy.lockLeaseTime());
            if (lock.isEmpty()) {
                JobContext context = new JobContext(schedule.jobName(), Instant.now(), 0, schedule.shardTotal(), null);
                executionRunner.skipped(request(JobExecutionTrigger.SCHEDULED, context, policy),
                        "single instance lock not acquired");
                JobObservationSupport.publish(
                        observationPublisher,
                        JobObservationSupport.OPERATION_SCHEDULER_RUN,
                        "local",
                        JobExecutionTrigger.SCHEDULED,
                        "skipped",
                        Map.of("skip_reason", "single_instance", "shard_presence", schedule.shardTotal() > 1 ? "true" : "false"),
                        null);
                return;
            }
            try (LockHandle ignored = lock.get()) {
                executeShards(job, schedule, policy);
            }
        } else {
            executeShards(job, schedule, policy);
        }
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_SCHEDULER_RUN,
                "local",
                JobExecutionTrigger.SCHEDULED,
                "completed",
                Map.of("shard_presence", schedule.shardTotal() > 1 ? "true" : "false"),
                null);
    }

    private void executeShards(NexaryJob job, JobSchedule schedule, JobExecutionPolicy policy) {
        List<String> workerIds = workerRegistry.workerIds(schedule);
        String currentWorkerId = workerRegistry.currentWorkerId(schedule);
        JobLoadBalanceStrategy strategy = effectiveLoadBalance(schedule);
        JobLoadBalancer loadBalancer = JobLoadBalancers.create(strategy);
        for (int shard = 0; shard < schedule.shardTotal(); shard++) {
            JobContext context = new JobContext(schedule.jobName(), Instant.now(), shard, schedule.shardTotal(), null);
            if (!shouldRunShard(schedule.jobName(), shard, workerIds, currentWorkerId, loadBalancer)) {
                executionRunner.skipped(request(JobExecutionTrigger.SCHEDULED, context, policy),
                        "shard assigned to another worker");
                continue;
            }
            AtomicInteger activeCount = activeCounts.computeIfAbsent(currentWorkerId == null ? "local" : currentWorkerId,
                    ignored -> new AtomicInteger());
            try {
                activeCount.incrementAndGet();
                executionRunner.execute(job, request(JobExecutionTrigger.SCHEDULED, context, policy));
            } finally {
                activeCount.decrementAndGet();
            }
        }
    }

    private boolean shouldRunShard(
            String jobName,
            int shard,
            List<String> workerIds,
            String currentWorkerId,
            JobLoadBalancer loadBalancer) {
        if (workerIds.isEmpty() || currentWorkerId == null) {
            return true;
        }
        List<JobWorker> workers = workerIds.stream()
                .map(workerId -> new JobWorker(workerId, activeCounts.getOrDefault(workerId, new AtomicInteger()).get(), 1))
                .toList();
        JobWorker selected = loadBalancer.select(jobName, shard, workers);
        return currentWorkerId.equals(selected.id());
    }

    private JobLoadBalanceStrategy effectiveLoadBalance(JobSchedule schedule) {
        return schedule.loadBalance() == null ? properties.getLoadBalance() : schedule.loadBalance();
    }

    private JobExecutionPolicy effectivePolicy(JobSchedule schedule) {
        return schedule.executionPolicy() == null ? properties.toExecutionPolicy() : schedule.executionPolicy();
    }

    private JobExecutionRequest request(JobExecutionTrigger trigger, JobContext context, JobExecutionPolicy policy) {
        return new JobExecutionRequest(null, trigger, context, policy);
    }
}
