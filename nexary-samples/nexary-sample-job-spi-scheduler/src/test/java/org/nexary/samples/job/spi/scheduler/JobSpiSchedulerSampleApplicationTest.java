package org.nexary.samples.job.spi.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.job.JobResult;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJobOperations;
import org.nexary.samples.job.business.SampleBusinessJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = JobSpiSchedulerSampleApplication.class)
class JobSpiSchedulerSampleApplicationTest {
    @Autowired
    private NexaryJobOperations jobs;

    @Test
    void schedulerProviderRunsWithoutBusinessCodeChanges() {
        JobResult result = jobs.trigger(SampleBusinessJob.JOB_NAME, 0, 1);
        jobs.schedule(new JobSchedule(SampleBusinessJob.JOB_NAME, "0 */10 * * * *", true, 1));

        assertThat(jobs.provider()).isEqualTo("local");
        assertThat(jobs.supportsScheduling()).isTrue();
        assertThat(result.status()).isEqualTo(JobResult.JobStatus.SUCCESS);
    }
}
