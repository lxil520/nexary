package org.nexary.job.store.redis.boot2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.Optional;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.internal.JobCompatibilityCollections;
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
        this.objectMapper = redisObjectMapper(objectMapper);
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
                    JobCompatibilityCollections.tags(
                            "store", "redis",
                            "shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                    null);
        } catch (JsonProcessingException ex) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_SAVE,
                    "redis",
                    record.trigger(),
                    "failed",
                    JobCompatibilityCollections.tags("store", "redis"),
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
                    JobCompatibilityCollections.tags("store", "redis"),
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
                    JobCompatibilityCollections.tags("store", "redis"),
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
                    JobCompatibilityCollections.tags(
                            "store", "redis",
                            "shard_presence", record.context().shardTotal() > 1 ? "true" : "false"),
                    null);
            return Optional.of(record);
        } catch (JsonProcessingException ex) {
            JobObservationSupport.publish(
                    observationPublisher,
                    JobObservationSupport.OPERATION_STORE_FIND,
                    "redis",
                    null,
                    "failed",
                    JobCompatibilityCollections.tags("store", "redis"),
                    ex);
            throw new IllegalStateException("Failed to deserialize Nexary job execution record " + executionId.value(), ex);
        }
    }

    private String key(JobExecutionId executionId) {
        return properties.getKeyPrefix() + executionId.value();
    }

    static ObjectMapper redisObjectMapper(ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper == null ? new ObjectMapper() : objectMapper.copy();
        mapper.registerModule(new JavaTimeModule());
        mapper.addMixIn(TrafficTag.class, TrafficTagMixin.class);
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper;
    }

    private abstract static class TrafficTagMixin {
        @JsonCreator
        TrafficTagMixin(
                @JsonProperty("channel") TrafficTag.Channel channel,
                @JsonProperty("priority") TrafficTag.Priority priority,
                @JsonProperty("tenant") String tenant,
                @JsonProperty("bizKey") String bizKey,
                @JsonProperty("attributes") Map<String, String> attributes) {
        }
    }
}
