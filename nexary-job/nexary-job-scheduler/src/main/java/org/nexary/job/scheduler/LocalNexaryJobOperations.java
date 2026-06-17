package org.nexary.job.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.NexaryJobScheduler;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionRequest;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionTrigger;

/** Local provider implementation of provider-neutral job operations. */
public class LocalNexaryJobOperations implements NexaryJobOperations {
    private final Map<String, NexaryJob> jobs;
    private final NexaryJobScheduler scheduler;
    private final JobExecutionRunner executionRunner;
    private final LocalJobSchedulerProperties properties;

    public LocalNexaryJobOperations(
            List<NexaryJob> jobs,
            NexaryJobScheduler scheduler,
            JobExecutionRunner executionRunner,
            LocalJobSchedulerProperties properties) {
        this.jobs = jobs.stream().collect(Collectors.toUnmodifiableMap(NexaryJob::name, Function.identity(), (left, right) -> left));
        this.scheduler = scheduler;
        this.executionRunner = executionRunner;
        this.properties = properties == null ? new LocalJobSchedulerProperties() : properties;
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public boolean supportsScheduling() {
        return true;
    }

    @Override
    public JobResult trigger(String jobName, int shardIndex, int shardTotal) {
        return triggerExecution(jobName, shardIndex, shardTotal).result();
    }

    @Override
    public JobExecutionRecord triggerExecution(String jobName, int shardIndex, int shardTotal) {
        NexaryJob job = Optional.ofNullable(jobs.get(jobName))
                .orElseThrow(() -> new IllegalArgumentException("unknown Nexary job: " + jobName));
        JobContext context = new JobContext(jobName, Instant.now(), shardIndex, shardTotal, null);
        return executionRunner.execute(job,
                new JobExecutionRequest(null, JobExecutionTrigger.DIRECT, context, properties.toExecutionPolicy()));
    }

    @Override
    public void schedule(JobSchedule schedule) {
        NexaryJob job = Optional.ofNullable(jobs.get(schedule.jobName()))
                .orElseThrow(() -> new IllegalArgumentException("unknown Nexary job: " + schedule.jobName()));
        scheduler.schedule(job, schedule);
    }

    @Override
    public boolean cancel(String jobName) {
        return scheduler.cancel(jobName);
    }

    @Override
    public Optional<JobExecutionRecord> execution(JobExecutionId executionId) {
        return executionRunner.record(executionId);
    }
}
