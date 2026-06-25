package org.nexary.job.execution;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernanceObservationEvents;
import org.nexary.core.governance.GovernanceRejection;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.TimeoutDecision;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.core.retry.RetrySignal;
import org.nexary.core.retry.RetryStopClassifier;
import org.nexary.core.retry.RetryStopReason;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.internal.JobCompatibilityCollections;

/** Shared provider-neutral pipeline for direct, scheduled, and bridge-triggered job executions. */
public class JobExecutionRunner {
    private final List<JobExecutionListener> listeners;
    private final ExecutorService executorService;
    private final JobExecutionStore executionStore;
    private final NexaryObservationPublisher observationPublisher;
    private final String provider;
    private final GovernanceExecution governanceExecution;
    private final Map<String, AtomicInteger> running = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> bulkheads = new ConcurrentHashMap<>();

    public JobExecutionRunner(List<JobExecutionListener> listeners, ExecutorService executorService) {
        this(listeners, executorService, new InMemoryJobExecutionStore());
    }

    /** Creates a runner backed by the given execution store. */
    public JobExecutionRunner(
            List<JobExecutionListener> listeners,
            ExecutorService executorService,
            JobExecutionStore executionStore) {
        this(listeners, executorService, executionStore, NexaryObservationPublisher.noop(), "unknown");
    }

    /** Creates a runner backed by the given execution store and observation publisher. */
    public JobExecutionRunner(
            List<JobExecutionListener> listeners,
            ExecutorService executorService,
            JobExecutionStore executionStore,
            NexaryObservationPublisher observationPublisher,
            String provider) {
        this(listeners, executorService, executionStore, observationPublisher, provider, GovernanceExecution.direct());
    }

    /** Creates a runner backed by the given execution store, observation publisher, and governance executor. */
    public JobExecutionRunner(
            List<JobExecutionListener> listeners,
            ExecutorService executorService,
            JobExecutionStore executionStore,
            NexaryObservationPublisher observationPublisher,
            String provider,
            GovernanceExecution governanceExecution) {
        this.listeners = JobCompatibilityCollections.copyList(listeners);
        this.executorService = executorService;
        this.executionStore = executionStore == null ? new InMemoryJobExecutionStore() : executionStore;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
        this.provider = provider == null || provider.trim().isEmpty() ? "unknown" : provider;
        this.governanceExecution = governanceExecution == null ? GovernanceExecution.direct() : governanceExecution;
    }

    /** Executes a job through timeout, retry, concurrency, listener, and record handling. */
    public JobExecutionRecord execute(NexaryJob job, JobExecutionRequest request) {
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_TRIGGER,
                provider,
                request.trigger(),
                "accepted",
                JobObservationSupport.shardTags(request),
                null);
        TimeoutDecision timeoutDecision = TimeoutDecision.from(governanceContext(request), Instant.now());
        if (!timeoutDecision.isAllowed()) {
            return skipped(request, timeoutDecision.reason());
        }
        if (isMisfired(request)) {
            return skipped(request, "scheduled execution misfired");
        }
        AtomicInteger bulkheadCount = null;
        boolean bulkheadAcquired = false;
        if (request.policy().maxConcurrentExecutions() < Integer.MAX_VALUE) {
            bulkheadCount = bulkheads.computeIfAbsent(bulkheadKey(request), ignored -> new AtomicInteger());
            if (!tryAcquire(bulkheadCount, request.policy().maxConcurrentExecutions())) {
                return skipped(request, "bulkhead rejected");
            }
            bulkheadAcquired = true;
        }
        String concurrencyKey = concurrencyKey(request);
        AtomicInteger runningCount = running.computeIfAbsent(concurrencyKey, ignored -> new AtomicInteger());
        boolean runningAcquired = false;
        try {
            if (request.policy().concurrencyPolicy() == JobConcurrencyPolicy.SKIP_IF_RUNNING
                    && runningCount.get() > 0) {
                return skipped(request, "same job shard is already running");
            }
            runningCount.incrementAndGet();
            runningAcquired = true;
            return executeWithRetry(job, request);
        } finally {
            if (runningAcquired) {
                runningCount.decrementAndGet();
            }
            if (bulkheadAcquired) {
                bulkheadCount.decrementAndGet();
            }
        }
    }

    /** Records a skipped execution without invoking the job handler. */
    public JobExecutionRecord skipped(JobExecutionRequest request, String message) {
        Instant now = Instant.now();
        JobResult result = new JobResult(JobResult.JobStatus.SKIPPED, message);
        JobExecutionRecord record = new JobExecutionRecord(
                request.executionId(),
                request.trigger(),
                request.context(),
                JobExecutionStatus.SKIPPED,
                result,
                0,
                now,
                now,
                Duration.ZERO,
                message,
                "",
                request.providerMetadata());
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_SKIP,
                provider,
                request.trigger(),
                JobObservationSupport.status(record.status()),
                merge(JobObservationSupport.shardTags(request), JobObservationSupport.skipTags(message)),
                null,
                now,
                now);
        publish(record, null);
        return record;
    }

    /** Returns one recorded execution by id. */
    public Optional<JobExecutionRecord> record(JobExecutionId id) {
        return executionStore.find(id);
    }

    private JobExecutionRecord executeWithRetry(NexaryJob job, JobExecutionRequest request) {
        Instant startedAt = Instant.now();
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_EXECUTION_START,
                provider,
                request.trigger(),
                "running",
                JobObservationSupport.shardTags(request),
                null,
                startedAt,
                startedAt);
        Throwable lastError = null;
        JobResult lastResult = null;
        int maxAttempts = request.policy().retryAttempts() + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_RETRY_ATTEMPT,
                    provider,
                    request.trigger(),
                    "running",
                    merge(JobObservationSupport.shardTags(request), JobObservationSupport.retryTags(attempt, maxAttempts)),
                    null);
            try {
                GovernanceContext governanceContext = governanceContext(request);
                lastResult = callWithTimeout(
                        () -> (JobResult) governanceExecution.execute(
                                governanceContext,
                                () -> job.execute(request.context())),
                        request.policy().timeout());
                if (lastResult != null && lastResult.status() == JobResult.JobStatus.SUCCESS) {
                    return complete(request, startedAt, attempt, lastResult, null);
                }
            } catch (Throwable error) {
                lastError = error;
                lastResult = error instanceof TimeoutException
                        ? new JobResult(JobResult.JobStatus.FAILED, "job execution timed out")
                        : JobResult.failed(error.getMessage());
                if (error instanceof GovernanceRejection) {
                    publishRetryStopped(request, RetryStopClassifier.fromGovernanceReason(
                            ((GovernanceRejection) error).governanceRejectionReason()), startedAt);
                    return complete(request, startedAt, attempt, lastResult, lastError);
                }
                if (error instanceof TimeoutException) {
                    JobObservationSupport.publish(
                            observationPublisher,
                            JobObservationSupport.OPERATION_TIMEOUT,
                            provider,
                            request.trigger(),
                            "timeout",
                            JobObservationSupport.shardTags(request),
                            error);
                }
                RetryStopReason stopReason = RetryStopClassifier.classify(error);
                if (stopReason != RetryStopReason.NONE) {
                    publishRetryStopped(request, stopReason, startedAt);
                    return complete(request, startedAt, attempt, lastResult, lastError);
                }
            }
            if (attempt < maxAttempts) {
                sleep(request.policy().retryBackoff());
            }
        }
        return complete(request, startedAt, maxAttempts, lastResult, lastError);
    }

    private JobExecutionRecord complete(
            JobExecutionRequest request,
            Instant startedAt,
            int attempts,
            JobResult result,
            Throwable error) {
        Instant endedAt = Instant.now();
        JobExecutionStatus status = status(result, error);
        JobExecutionRecord record = new JobExecutionRecord(
                request.executionId(),
                request.trigger(),
                request.context(),
                status,
                result,
                attempts,
                startedAt,
                endedAt,
                Duration.between(startedAt, endedAt),
                result == null ? "" : result.message(),
                error == null ? "" : error.getMessage(),
                request.providerMetadata());
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_EXECUTION_END,
                provider,
                request.trigger(),
                JobObservationSupport.status(status),
                JobObservationSupport.shardTags(request),
                error,
                startedAt,
                endedAt);
        publish(record, error);
        return record;
    }

    private JobResult callWithTimeout(Callable<JobResult> callable, Duration timeout) throws Exception {
        CompletableFuture<JobResult> future = CompletableFuture.supplyAsync(() -> {
            try {
                return callable.call();
            } catch (Exception ex) {
                throw new JobExecutionException(ex);
            }
        }, executorService);
        try {
            return future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof JobExecutionException) {
                Throwable original = cause.getCause();
                if (original instanceof Exception) {
                    throw (Exception) original;
                }
                throw new RuntimeException(original);
            }
            throw ex;
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ex;
        }
    }

    private boolean isMisfired(JobExecutionRequest request) {
        if (request.trigger() != JobExecutionTrigger.SCHEDULED
                || request.policy().misfirePolicy() != JobMisfirePolicy.SKIP) {
            return false;
        }
        Instant scheduledAt = request.context().scheduledAt();
        return scheduledAt.plus(request.policy().misfireThreshold()).isBefore(Instant.now());
    }

    private JobExecutionStatus status(JobResult result, Throwable error) {
        if (error instanceof TimeoutException) {
            return JobExecutionStatus.TIMEOUT;
        }
        if (result == null) {
            return JobExecutionStatus.FAILED;
        }
        if (result.status() == JobResult.JobStatus.SUCCESS) {
            return JobExecutionStatus.SUCCESS;
        }
        if (result.status() == JobResult.JobStatus.SKIPPED) {
            return JobExecutionStatus.SKIPPED;
        }
        return JobExecutionStatus.FAILED;
    }

    private String concurrencyKey(JobExecutionRequest request) {
        return request.context().jobName() + ':' + request.context().shardIndex() + ':' + request.context().shardTotal();
    }

    private String bulkheadKey(JobExecutionRequest request) {
        return request.context().jobName() + ':' + JobObservationSupport.trigger(request.trigger());
    }

    private boolean tryAcquire(AtomicInteger count, int max) {
        while (true) {
            int current = count.get();
            if (current >= max) {
                return false;
            }
            if (count.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private GovernanceContext governanceContext(JobExecutionRequest request) {
        GovernanceContext.Builder builder = GovernanceContext.builder()
                .resource(GovernanceResource.job(request.context().jobName(), JobObservationSupport.trigger(request.trigger())))
                .trafficTag(request.context().trafficTag())
                .attribute("trigger", JobObservationSupport.trigger(request.trigger()));
        effectiveDeadline(request).ifPresent(builder::deadline);
        return builder.build();
    }

    private void publishRetryStopped(JobExecutionRequest request, RetryStopReason reason, Instant startedAt) {
        if (reason == null || reason == RetryStopReason.NONE) {
            return;
        }
        Instant now = Instant.now();
        observationPublisher.publish(GovernanceObservationEvents.retryStopped(
                governanceContext(request).resource(),
                request.context().trafficTag(),
                RetrySignal.stop(reason),
                startedAt,
                now));
    }

    private Optional<Instant> effectiveDeadline(JobExecutionRequest request) {
        Optional<Instant> inherited = GovernanceContext.current().flatMap(GovernanceContext::deadline);
        Optional<Instant> policyDeadline = request.policy().startDeadline()
                .map(duration -> request.context().scheduledAt().plus(duration));
        if (!inherited.isPresent()) {
            return policyDeadline;
        }
        if (!policyDeadline.isPresent()) {
            return inherited;
        }
        return Optional.of(inherited.get().isBefore(policyDeadline.get()) ? inherited.get() : policyDeadline.get());
    }

    private void publish(JobExecutionRecord record, Throwable error) {
        executionStore.save(record);
        for (JobExecutionListener listener : listeners) {
            try {
                listener.afterExecution(record.context(), record.result(), error);
                listener.afterExecution(record);
                JobObservationSupport.publish(
                        observationPublisher,
                        JobObservationSupport.OPERATION_LISTENER_NOTIFICATION,
                        provider,
                        record.trigger(),
                        JobObservationSupport.status(record.status()),
                        JobCompatibilityCollections.tags(
                                "shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                        null);
            } catch (RuntimeException ex) {
                JobObservationSupport.publish(
                        observationPublisher,
                        JobObservationSupport.OPERATION_LISTENER_NOTIFICATION,
                        provider,
                        record.trigger(),
                        "failed",
                        JobCompatibilityCollections.tags(
                                "shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                        ex);
                throw ex;
            }
        }
    }

    private Map<String, String> merge(Map<String, String> left, Map<String, String> right) {
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return merged;
    }

    private void sleep(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class JobExecutionException extends RuntimeException {
        private JobExecutionException(Throwable cause) {
            super(cause);
        }
    }
}
