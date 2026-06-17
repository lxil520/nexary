package org.nexary.samples.job.processor;

import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.springframework.stereotype.Component;

/** Component-scanned processor job handler. */
@Component
public class ProcessorBusinessJob implements NexaryJob {
    public static final String JOB_NAME = "processor-business-job";

    @Override
    public String name() {
        return JOB_NAME;
    }

    @Override
    public JobResult execute(JobContext context) {
        return new JobResult(JobResult.JobStatus.SUCCESS,
                "processed shard " + context.shardIndex() + "/" + context.shardTotal());
    }
}
