package org.nexary.job.store.redis.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.TrafficTag;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionTrigger;

class RedisBoot2JobExecutionStoreSerializationTest {
    @Test
    void serializesAndDeserializesExecutionRecordOnBoot2ProviderLine() throws Exception {
        ObjectMapper objectMapper = RedisJobExecutionStore.redisObjectMapper(new ObjectMapper());
        Instant now = Instant.parse("2026-06-17T08:00:00Z");
        JobExecutionRecord record = new JobExecutionRecord(
                new JobExecutionId("boot2-execution-json-1"),
                JobExecutionTrigger.BRIDGE,
                new JobContext("boot2-redis-json-job", now, 2, 5, TrafficTag.builder().tenant("boot2").build()),
                JobExecutionStatus.SUCCESS,
                JobResult.success(),
                2,
                now,
                now.plusMillis(12),
                Duration.ofMillis(12),
                "ok",
                "");

        String json = objectMapper.writeValueAsString(record);
        JobExecutionRecord restored = objectMapper.readValue(json, JobExecutionRecord.class);

        assertThat(restored).isEqualTo(record);
        assertThat(restored.executionId().value()).isEqualTo("boot2-execution-json-1");
        assertThat(restored.context().trafficTag().tenant()).isEqualTo("boot2");
        assertThat(restored.context().shardIndex()).isEqualTo(2);
        assertThat(restored.context().shardTotal()).isEqualTo(5);
        assertThat(restored.trigger()).isEqualTo(JobExecutionTrigger.BRIDGE);
    }
}
