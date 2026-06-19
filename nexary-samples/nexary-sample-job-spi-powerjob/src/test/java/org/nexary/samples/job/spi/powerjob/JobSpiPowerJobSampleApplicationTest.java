package org.nexary.samples.job.spi.powerjob;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJobOperations;
import org.nexary.samples.job.business.SampleBusinessJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = JobSpiPowerJobSampleApplication.class)
class JobSpiPowerJobSampleApplicationTest {
    @Autowired
    private NexaryJobOperations jobs;

    @Test
    void powerJobProviderRunsWithoutBusinessCodeChanges() {
        JobResult result = jobs.trigger(SampleBusinessJob.JOB_NAME, 1, 4);

        assertThat(jobs.provider()).isEqualTo("powerjob");
        assertThat(jobs.supportsScheduling()).isFalse();
        assertThat(result.status()).isEqualTo(JobResult.JobStatus.SUCCESS);
    }
}
