package org.nexary.job.xxljob.boot4;

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

/** Bridges externally triggered XXL-JOB handlers to Nexary jobs and listeners. */
public class XxlJobBridge {
    private final Map<String, NexaryJob> jobs;
    private final JobExecutionRunner executionRunner;
    private final XxlJobProperties properties;
    private final NexaryObservationPublisher observationPublisher;

    public XxlJobBridge(List<NexaryJob> jobs, JobExecutionRunner executionRunner, XxlJobProperties properties) {
        this(jobs, executionRunner, properties, NexaryObservationPublisher.noop());
    }

    public XxlJobBridge(
            List<NexaryJob> jobs,
            JobExecutionRunner executionRunner,
            XxlJobProperties properties,
            NexaryObservationPublisher observationPublisher) {
        this.jobs = jobs.stream().collect(Collectors.toUnmodifiableMap(NexaryJob::name, Function.identity(), (left, right) -> left));
        this.executionRunner = executionRunner;
        this.properties = properties == null ? new XxlJobProperties() : properties;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    /** Triggers a named job using shard information from XXL-JOB when available. */
    public JobResult trigger(String jobName) {
        return trigger(jobName, XxlJobRuntime.currentShardIndex(), XxlJobRuntime.currentShardTotal());
    }

    /** Triggers a named job with explicit shard metadata. */
    public JobResult trigger(String jobName, int shardIndex, int shardTotal) {
        return triggerExecution(jobName, shardIndex, shardTotal).result();
    }

    /** Triggers a named job and returns the provider-neutral execution record. */
    public JobExecutionRecord triggerExecution(String jobName, int shardIndex, int shardTotal) {
        JobObservationSupport.publish(
                observationPublisher,
                JobObservationSupport.OPERATION_XXLJOB_BRIDGE_TRIGGER,
                "xxljob",
                JobExecutionTrigger.BRIDGE,
                "accepted",
                JobCompatibilityCollections.tags("shard_presence", shardTotal > 1 ? "true" : "false"),
                null);
        NexaryJob job = Optional.ofNullable(jobs.get(jobName))
                .orElseThrow(() -> new IllegalArgumentException("unknown Nexary job: " + jobName));
        JobContext context = new JobContext(jobName, Instant.now(), shardIndex, shardTotal, null);
        JobExecutionRecord record = executionRunner.execute(job,
                new JobExecutionRequest(null, JobExecutionTrigger.BRIDGE, context, properties.toExecutionPolicy()));
        XxlJobRuntime.log("Nexary job {} finished with {}", jobName, record.status());
        return record;
    }

    /** Returns one execution record by id when still retained in memory. */
    public Optional<JobExecutionRecord> execution(JobExecutionId executionId) {
        return executionRunner.record(executionId);
    }
}
