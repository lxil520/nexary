package org.nexary.samples.job.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJobOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = JobProcessorSampleApplication.class, properties = "spring.profiles.active=processor")
class JobProcessorSampleApplicationTest {
    @Autowired
    private NexaryJobOperations jobs;

    @Test
    void processorStartsWithoutWebServerAndRunsJob() {
        JobResult result = jobs.trigger(ProcessorBusinessJob.JOB_NAME, 0, 1);

        assertThat(result.status()).isEqualTo(JobResult.JobStatus.SUCCESS);
        assertThat(result.message()).isEqualTo("processed shard 0/1");
    }
}
