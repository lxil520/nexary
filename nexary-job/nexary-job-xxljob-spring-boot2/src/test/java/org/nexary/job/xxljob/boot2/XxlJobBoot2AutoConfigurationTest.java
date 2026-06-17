package org.nexary.job.xxljob.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.execution.JobExecutionRecord;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class XxlJobBoot2AutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(XxlJobAutoConfiguration.class))
            .withUserConfiguration(JobConfiguration.class)
            .withPropertyValues("nexary.job.provider=xxljob");

    @Test
    void createsBridgeOperationsForBoot2Line() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(XxlJobBridge.class);
            assertThat(context).hasSingleBean(NexaryJobOperations.class);

            NexaryJobOperations operations = context.getBean(NexaryJobOperations.class);
            assertThat(operations.provider()).isEqualTo("xxljob");
            assertThat(operations.supportsScheduling()).isFalse();
            JobExecutionRecord record = operations.triggerExecution("boot2-xxljob-job", 2, 5);
            assertThat(record.result()).isEqualTo(JobResult.success());
            assertThat(record.context().shardIndex()).isEqualTo(2);
            assertThat(record.context().shardTotal()).isEqualTo(5);
            assertThat(operations.execution(record.executionId())).contains(record);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class JobConfiguration {
        @Bean
        NexaryJob boot2XxlJob() {
            return new NexaryJob() {
                @Override
                public String name() {
                    return "boot2-xxljob-job";
                }

                @Override
                public JobResult execute(org.nexary.job.JobContext context) {
                    return JobResult.success();
                }
            };
        }
    }
}
