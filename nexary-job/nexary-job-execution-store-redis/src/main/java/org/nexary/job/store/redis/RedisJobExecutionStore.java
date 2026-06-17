package org.nexary.job.store.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionStore;
import org.nexary.job.execution.JobObservationSupport;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis-backed durable store for completed job execution records. */
public class RedisJobExecutionStore implements JobExecutionStore {
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisJobExecutionStoreProperties properties;
    private final NexaryObservationPublisher observationPublisher;

    public RedisJobExecutionStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisJobExecutionStoreProperties properties) {
        this(stringRedisTemplate, objectMapper, properties, NexaryObservationPublisher.noop());
    }

    public RedisJobExecutionStore(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisJobExecutionStoreProperties properties,
            NexaryObservationPublisher observationPublisher) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public void save(JobExecutionRecord record) {
        if (record == null) {
            return;
        }
        try {
            stringRedisTemplate.opsForValue()
                    .set(key(record.executionId()), objectMapper.writeValueAsString(record), properties.getRetention());
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_SAVE,
                    "redis",
                    record.trigger(),
                    JobObservationSupport.status(record.status()),
                    Map.of("store", "redis", "shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                    null);
        } catch (JsonProcessingException ex) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_SAVE,
                    "redis",
                    record.trigger(),
                    "failed",
                    Map.of("store", "redis"),
                    ex);
            throw new IllegalStateException("Failed to serialize Nexary job execution record " + record.executionId().value(), ex);
        }
    }

    @Override
    public Optional<JobExecutionRecord> find(JobExecutionId executionId) {
        if (executionId == null) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_FIND,
                    "redis",
                    null,
                    "miss",
                    Map.of("store", "redis"),
                    null);
            return Optional.empty();
        }
        String serializedRecord = stringRedisTemplate.opsForValue().get(key(executionId));
        if (serializedRecord == null) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_RETENTION_EXPIRY,
                    "redis",
                    null,
                    "expired",
                    Map.of("store", "redis"),
                    null);
            return Optional.empty();
        }
        try {
            JobExecutionRecord record = objectMapper.readValue(serializedRecord, JobExecutionRecord.class);
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_FIND,
                    "redis",
                    record.trigger(),
                    JobObservationSupport.status(record.status()),
                    Map.of("store", "redis", "shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                    null);
            return Optional.of(record);
        } catch (JsonProcessingException ex) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_FIND,
                    "redis",
                    null,
                    "failed",
                    Map.of("store", "redis"),
                    ex);
            throw new IllegalStateException("Failed to deserialize Nexary job execution record " + executionId.value(), ex);
        }
    }

    private String key(JobExecutionId executionId) {
        return properties.getKeyPrefix() + executionId.value();
    }
}
