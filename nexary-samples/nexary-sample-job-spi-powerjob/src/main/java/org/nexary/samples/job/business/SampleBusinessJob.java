package org.nexary.samples.job.business;

import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.springframework.stereotype.Component;

/** Business job handler that depends only on Nexary job API. */
@Component
public class SampleBusinessJob implements NexaryJob {
    public static final String JOB_NAME = "sample-business-job";

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
