package org.nexary.boot.job.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.powerjob.boot2.PowerJobAutoConfiguration;
import org.nexary.job.scheduler.boot2.LocalJobSchedulerAutoConfiguration;
import org.nexary.job.store.redis.boot2.RedisJobExecutionStoreAutoConfiguration;
import org.nexary.job.xxljob.boot2.XxlJobAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

class JobBoot2StarterDependencyTest {
    @Test
    void starterExposesJobApiAndBoot2ProviderLine() throws Exception {
        assertThat(NexaryJobOperations.class.getName()).isEqualTo("org.nexary.job.NexaryJobOperations");
        assertThat(LocalJobSchedulerAutoConfiguration.class.getName())
                .isEqualTo("org.nexary.job.scheduler.boot2.LocalJobSchedulerAutoConfiguration");
        assertThat(XxlJobAutoConfiguration.class.getName())
                .isEqualTo("org.nexary.job.xxljob.boot2.XxlJobAutoConfiguration");
        assertThat(PowerJobAutoConfiguration.class.getName())
                .isEqualTo("org.nexary.job.powerjob.boot2.PowerJobAutoConfiguration");
        assertThat(RedisJobExecutionStoreAutoConfiguration.class.getName())
                .isEqualTo("org.nexary.job.store.redis.boot2.RedisJobExecutionStoreAutoConfiguration");

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")) {
            assertThat(stream).isNotNull();
            String factories = read(stream);
            assertThat(factories).contains(EnableAutoConfiguration.class.getName());
            assertThat(factories).contains("org.nexary.job.scheduler.boot2.LocalJobSchedulerAutoConfiguration");
            assertThat(factories).contains("org.nexary.job.xxljob.boot2.XxlJobAutoConfiguration");
            assertThat(factories).contains("org.nexary.job.powerjob.boot2.PowerJobAutoConfiguration");
            assertThat(factories).contains("org.nexary.job.store.redis.boot2.RedisJobExecutionStoreAutoConfiguration");
        }
    }

    private static String read(InputStream stream) {
        Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}
