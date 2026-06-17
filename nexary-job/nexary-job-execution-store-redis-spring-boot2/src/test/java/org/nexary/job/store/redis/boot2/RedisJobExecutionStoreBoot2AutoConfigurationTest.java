package org.nexary.job.store.redis.boot2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.nexary.job.execution.JobExecutionStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisJobExecutionStoreBoot2AutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisJobExecutionStoreAutoConfiguration.class))
            .withUserConfiguration(RedisConfiguration.class)
            .withPropertyValues("nexary.job.execution.store.redis.enabled=true");

    @Test
    void createsRedisExecutionStoreForBoot2Line() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JobExecutionStore.class);
            assertThat(context.getBean(JobExecutionStore.class)).isInstanceOf(RedisJobExecutionStore.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RedisConfiguration {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }
}
