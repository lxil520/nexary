package org.nexary.job.powerjob.boot4;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionRequest;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.internal.JobCompatibilityCollections;
import org.nexary.job.execution.JobObservationSupport;

/** Bridges externally triggered PowerJob handlers to Nexary jobs and listeners. */
public class PowerJobBridge {
    private final Map<String, NexaryJob> jobs;
    private final JobExecutionRunner executionRunner;
    private final PowerJobProperties properties;
    private final NexaryObservationPublisher observationPublisher;

    public PowerJobBridge(List<NexaryJob> jobs, JobExecutionRunner executionRunner, PowerJobProperties properties) {
        this(jobs, executionRunner, properties, NexaryObservationPublisher.noop());
    }

    public PowerJobBridge(
            List<NexaryJob> jobs,
            JobExecutionRunner executionRunner,
            PowerJobProperties properties,
            NexaryObservationPublisher observationPublisher) {
        this.jobs = jobs.stream().collect(Collectors.toUnmodifiableMap(NexaryJob::name, Function.identity(), (left, right) -> left));
        this.executionRunner = executionRunner;
        this.properties = properties == null ? new PowerJobProperties() : properties;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Triggers a named job without platform shard metadata. */
    public JobResult trigger(String jobName) {
        return triggerExecution(new PowerJobBridgeRequest(jobName)).result();
    }

    /** Triggers a named job with explicit shard metadata. */
    public JobResult trigger(String jobName, int shardIndex, int shardTotal) {
        return triggerExecution(jobName, shardIndex, shardTotal).result();
    }

    /** Triggers a named job and returns the provider-neutral execution record. */
    public JobExecutionRecord triggerExecution(String jobName, int shardIndex, int shardTotal) {
        return triggerExecution(new PowerJobBridgeRequest(jobName, shardIndex, shardTotal, "", "", ""));
    }

    /** Triggers a named job with provider-side PowerJob metadata. */
    public JobExecutionRecord triggerExecution(PowerJobBridgeRequest request) {
        PowerJobBridgeRequest safeRequest = request == null ? new PowerJobBridgeRequest("unknown") : request;
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_POWERJOB_BRIDGE_TRIGGER,
                "powerjob",
                JobExecutionTrigger.BRIDGE,
                "accepted",
                JobCompatibilityCollections.tags("shard_presence", safeRequest.shardTotal() > 1 ? "true" : "false"),
                null);
        NexaryJob job = Optional.ofNullable(jobs.get(safeRequest.jobName()))
                .orElseThrow(() -> new IllegalArgumentException("unknown Nexary job: " + safeRequest.jobName()));
        JobContext context = new JobContext(safeRequest.jobName(), Instant.now(), safeRequest.shardIndex(), safeRequest.shardTotal(), null);
        JobExecutionRecord record = executionRunner.execute(job,
                new JobExecutionRequest(null, JobExecutionTrigger.BRIDGE, context, properties.toExecutionPolicy(), safeRequest.providerMetadata()));
        PowerJobRuntime.log("Nexary job " + safeRequest.jobName() + " finished with " + record.status());
        return record;
    }

    /** Returns one execution record by id when still retained in memory. */
    public Optional<JobExecutionRecord> execution(JobExecutionId executionId) {
        return executionRunner.record(executionId);
    }
}
