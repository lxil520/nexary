package org.nexary.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobContextTest {
    @Test
    void normalizesShardValuesAndTrafficTag() {
        JobContext context = new JobContext("job", null, -1, 0, null);

        assertThat(context.shardIndex()).isZero();
        assertThat(context.shardTotal()).isEqualTo(1);
        assertThat(context.trafficTag()).isNotNull();
        assertThat(context.scheduledAt()).isNotNull();
    }
}
