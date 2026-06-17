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
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;

/** Shared provider-neutral pipeline for direct, scheduled, and bridge-triggered job executions. */
public class JobExecutionRunner {
    private final List<JobExecutionListener> listeners;
    private final ExecutorService executorService;
    private final JobExecutionStore executionStore;
    private final NexaryObservationPublisher observationPublisher;
    private final String provider;
    private final Map<String, AtomicInteger> running = new ConcurrentHashMap<>();

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
        this.listeners = List.copyOf(listeners);
        this.executorService = executorService;
        this.executionStore = executionStore == null ? new InMemoryJobExecutionStore() : executionStore;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
        this.provider = provider == null || provider.isBlank() ? "unknown" : provider;
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
        if (isMisfired(request)) {
            return skipped(request, "scheduled execution misfired");
        }
        String concurrencyKey = concurrencyKey(request);
        AtomicInteger runningCount = running.computeIfAbsent(concurrencyKey, ignored -> new AtomicInteger());
        if (request.policy().concurrencyPolicy() == JobConcurrencyPolicy.SKIP_IF_RUNNING
                && runningCount.get() > 0) {
            return skipped(request, "same job shard is already running");
        }
        runningCount.incrementAndGet();
        try {
            return executeWithRetry(job, request);
        } finally {
            runningCount.decrementAndGet();
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
                "");
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
                lastResult = callWithTimeout(() -> job.execute(request.context()), request.policy().timeout());
                if (lastResult != null && lastResult.status() == JobResult.JobStatus.SUCCESS) {
                    return complete(request, startedAt, attempt, lastResult, null);
                }
            } catch (Throwable error) {
                lastError = error;
                lastResult = error instanceof TimeoutException
                        ? new JobResult(JobResult.JobStatus.FAILED, "job execution timed out")
                        : JobResult.failed(error.getMessage());
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
                error == null ? "" : error.getMessage());
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
            if (cause instanceof JobExecutionException wrapper) {
                Throwable original = wrapper.getCause();
                if (original instanceof Exception exception) {
                    throw exception;
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
        return switch (result.status()) {
            case SUCCESS -> JobExecutionStatus.SUCCESS;
            case FAILED -> JobExecutionStatus.FAILED;
            case SKIPPED -> JobExecutionStatus.SKIPPED;
        };
    }

    private String concurrencyKey(JobExecutionRequest request) {
        return request.context().jobName() + ':' + request.context().shardIndex() + ':' + request.context().shardTotal();
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
                        Map.of("shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                        null);
            } catch (RuntimeException ex) {
                JobObservationSupport.publish(
                        observationPublisher,
                        JobObservationSupport.OPERATION_LISTENER_NOTIFICATION,
                        provider,
                        record.trigger(),
                        "failed",
                        Map.of("shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
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
