package org.nexary.samples.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.job.JobResult;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJobOperations;
import org.nexary.samples.job.app.JobSampleApplication;
import org.nexary.samples.job.business.SampleBusinessJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = JobSampleApplication.class)
class JobSampleApplicationTest {
    @Autowired
    private NexaryJobOperations jobs;

    @Test
    void frameworkFindsBusinessJobByNameAndRunsIt() {
        JobResult result = jobs.trigger(SampleBusinessJob.JOB_NAME, 0, 1);

        assertThat(jobs.provider()).isEqualTo("local");
        assertThat(result.status()).isEqualTo(JobResult.JobStatus.SUCCESS);
        assertThat(result.message()).isEqualTo("processed shard 0/1");
    }

    @Test
    void localProviderSchedulesBusinessJobByName() {
        jobs.schedule(new JobSchedule(SampleBusinessJob.JOB_NAME, "0 */10 * * * *", true, 1));

        assertThat(jobs.supportsScheduling()).isTrue();
    }

    @Test
    void businessJobDoesNotDependOnProviderWiring() {
        assertThat(SampleBusinessJob.class.getDeclaredFields())
                .extracting(field -> field.getType().getName())
                .noneMatch(type -> type.contains(".mode.") || type.contains(".xxljob.") || type.contains(".scheduler."));
    }
}
