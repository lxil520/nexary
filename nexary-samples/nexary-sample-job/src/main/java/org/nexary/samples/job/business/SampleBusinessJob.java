package org.nexary.samples.job.business;

import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.springframework.stereotype.Component;

/** Business job handler that only depends on Nexary job abstractions. */
@Component
public class SampleBusinessJob implements NexaryJob {
    public static final String JOB_NAME = "sample-business-job";

    private final SampleBusinessService businessService;

    public SampleBusinessJob(SampleBusinessService businessService) {
        this.businessService = businessService;
    }

    @Override
    public String name() {
        return JOB_NAME;
    }

    @Override
    public JobResult execute(JobContext context) {
        SampleBusinessService.BusinessReceipt receipt = businessService.run(context);
        return new JobResult(JobResult.JobStatus.SUCCESS, receipt.message());
    }
}
