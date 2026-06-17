package org.nexary.samples.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJobOperations;
import org.nexary.samples.job.app.JobSampleApplication;
import org.nexary.samples.job.business.SampleBusinessJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = JobSampleApplication.class,
        properties = "spring.profiles.active=xxljob")
class XxlJobSampleApplicationTest {
    @Autowired
    private NexaryJobOperations jobs;

    @Test
    void xxlJobProfileTriggersSameBusinessJobThroughBridge() {
        JobResult result = jobs.trigger(SampleBusinessJob.JOB_NAME, 1, 4);

        assertThat(jobs.provider()).isEqualTo("xxljob");
        assertThat(jobs.supportsScheduling()).isFalse();
        assertThat(result.status()).isEqualTo(JobResult.JobStatus.SUCCESS);
        assertThat(result.message()).isEqualTo("processed shard 1/4");
    }
}
