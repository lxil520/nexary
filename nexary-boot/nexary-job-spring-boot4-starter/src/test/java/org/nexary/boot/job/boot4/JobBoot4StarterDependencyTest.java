package org.nexary.boot.job.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.scheduler.boot4.LocalJobSchedulerAutoConfiguration;
import org.nexary.job.store.redis.boot4.RedisJobExecutionStoreAutoConfiguration;
import org.nexary.job.xxljob.boot4.XxlJobAutoConfiguration;

class JobBoot4StarterDependencyTest {
    @Test
    void starterExposesJobApiAndBoot4ProviderLine() throws Exception {
        assertThat(NexaryJobOperations.class.getName()).isEqualTo("org.nexary.job.NexaryJobOperations");
        assertThat(LocalJobSchedulerAutoConfiguration.class.getName())
                .isEqualTo("org.nexary.job.scheduler.boot4.LocalJobSchedulerAutoConfiguration");
        assertThat(XxlJobAutoConfiguration.class.getName())
                .isEqualTo("org.nexary.job.xxljob.boot4.XxlJobAutoConfiguration");
        assertThat(RedisJobExecutionStoreAutoConfiguration.class.getName())
                .isEqualTo("org.nexary.job.store.redis.boot4.RedisJobExecutionStoreAutoConfiguration");

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(stream).isNotNull();
            String imports = read(stream);
            assertThat(imports).contains("org.nexary.job.scheduler.boot4.LocalJobSchedulerAutoConfiguration");
            assertThat(imports).contains("org.nexary.job.xxljob.boot4.XxlJobAutoConfiguration");
            assertThat(imports).contains("org.nexary.job.store.redis.boot4.RedisJobExecutionStoreAutoConfiguration");
        }
    }

    private static String read(InputStream stream) {
        Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}
