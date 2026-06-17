package org.nexary.job.scheduler.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.NexaryJobScheduler;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;

class LocalJobSchedulerBoot2AutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LocalJobSchedulerAutoConfiguration.class))
            .withUserConfiguration(JobConfiguration.class);

    @Test
    void createsLocalSchedulerOperationsAndExecutionStoreForBoot2Line() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskScheduler.class);
            assertThat(context).hasSingleBean(JobExecutionStore.class);
            assertThat(context).hasSingleBean(NexaryJobScheduler.class);
            assertThat(context).hasSingleBean(NexaryJobOperations.class);

            NexaryJobOperations operations = context.getBean(NexaryJobOperations.class);
            assertThat(operations.provider()).isEqualTo("local");
            assertThat(operations.supportsScheduling()).isTrue();
            JobExecutionRecord record = operations.triggerExecution("boot2-local-job", 0, 1);
            assertThat(record.result()).isEqualTo(JobResult.success());
            assertThat(operations.execution(record.executionId())).contains(record);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class JobConfiguration {
        @Bean
        NexaryJob boot2LocalJob() {
            return new NexaryJob() {
                @Override
                public String name() {
                    return "boot2-local-job";
                }

                @Override
                public JobResult execute(org.nexary.job.JobContext context) {
                    return JobResult.success();
                }
            };
        }
    }
}
