package org.nexary.job.store.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionRequest;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionStore;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.execution.JobObservationSupport;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisJobExecutionStoreIntegrationTest {
    @Test
    void persistsLifecycleRecordsAcrossStoreObjectRecreation() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisJobExecutionStoreProperties properties = properties(Duration.ofSeconds(10));
            RecordingObservationPublisher publisher = new RecordingObservationPublisher();
            JobExecutionStore store = store(fixture.stringRedisTemplate, properties, publisher);
            ExecutorService executor = Executors.newCachedThreadPool();
            try {
                JobExecutionRunner runner = new JobExecutionRunner(List.of(), executor, store, publisher, "redis-test");
                JobExecutionRecord success = runner.execute(job(context -> JobResult.success()),
                        request(JobExecutionTrigger.DIRECT, 1, 4, JobExecutionPolicy.defaults()));
                JobExecutionRecord failure = runner.execute(job(context -> JobResult.failed("failed")),
                        request(JobExecutionTrigger.DIRECT, 0, 1, JobExecutionPolicy.defaults()));
                AtomicInteger retryAttempts = new AtomicInteger();
                JobExecutionRecord retrySuccess = runner.execute(job(context -> retryAttempts.incrementAndGet() == 1
                                ? JobResult.failed("temporary")
                                : JobResult.success()),
                        request(JobExecutionTrigger.SCHEDULED, 2, 4, JobExecutionPolicy.defaults()
                                .withRetryAttempts(1)
                                .withRetryBackoff(Duration.ZERO)));
                JobExecutionRecord timeout = runner.execute(job(context -> {
                            Thread.sleep(200);
                            return JobResult.success();
                        }),
                        request(JobExecutionTrigger.DIRECT, 0, 1, JobExecutionPolicy.defaults()
                                .withTimeout(Duration.ofMillis(20))));
                JobExecutionRecord skipped = runner.skipped(
                        request(JobExecutionTrigger.SCHEDULED, 3, 4, JobExecutionPolicy.defaults()),
                        "shard assigned to another worker");
                JobExecutionRecord bridge = runner.execute(job(context -> JobResult.success()),
                        request(JobExecutionTrigger.BRIDGE, 2, 5, JobExecutionPolicy.defaults()));

                JobExecutionStore recreated = store(fixture.stringRedisTemplate, properties, publisher);

                assertThat(recreated.find(success.executionId()).map(JobExecutionRecord::status))
                        .contains(JobExecutionStatus.SUCCESS);
                assertThat(recreated.find(failure.executionId()).map(JobExecutionRecord::status))
                        .contains(JobExecutionStatus.FAILED);
                assertThat(recreated.find(retrySuccess.executionId()).map(JobExecutionRecord::attempts))
                        .contains(2);
                assertThat(recreated.find(timeout.executionId()).map(JobExecutionRecord::status))
                        .contains(JobExecutionStatus.TIMEOUT);
                assertThat(recreated.find(skipped.executionId()).map(JobExecutionRecord::message))
                        .contains("shard assigned to another worker");
                Optional<JobExecutionRecord> bridgeRecord = recreated.find(bridge.executionId());
                assertThat(bridgeRecord.map(JobExecutionRecord::trigger)).contains(JobExecutionTrigger.BRIDGE);
                assertThat(bridgeRecord.map(record -> record.context().shardIndex())).contains(2);
                assertThat(bridgeRecord.map(record -> record.context().shardTotal())).contains(5);
                assertThat(publisher.operations()).contains(
                        JobObservationSupport.OPERATION_STORE_SAVE,
                        JobObservationSupport.OPERATION_STORE_FIND,
                        JobObservationSupport.OPERATION_TRIGGER,
                        JobObservationSupport.OPERATION_TIMEOUT);
                assertThat(publisher.events).allSatisfy(event ->
                        assertThat(event.tags()).doesNotContainKeys("execution_id", "payload", "exception_message", "stack_trace"));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void expiresRecordsAfterConfiguredRetention() {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        try (RedisFixture fixture = new RedisFixture()) {
            RedisJobExecutionStoreProperties properties = properties(Duration.ofMillis(150));
            RecordingObservationPublisher publisher = new RecordingObservationPublisher();
            JobExecutionStore store = store(fixture.stringRedisTemplate, properties, publisher);
            JobExecutionRecord record = record();

            store.save(record);

            assertThat(store.find(record.executionId())).contains(record);
            assertEventually(() -> store.find(record.executionId()), Optional.empty());
            assertThat(publisher.operations()).contains(JobObservationSupport.OPERATION_STORE_RETENTION_EXPIRY);
        }
    }

    private RedisJobExecutionStoreProperties properties(Duration retention) {
        RedisJobExecutionStoreProperties properties = new RedisJobExecutionStoreProperties();
        properties.setEnabled(true);
        properties.setKeyPrefix("nexary:test:job:execution:" + UUID.randomUUID() + ':');
        properties.setRetention(retention);
        return properties;
    }

    private JobExecutionStore store(StringRedisTemplate stringRedisTemplate, RedisJobExecutionStoreProperties properties) {
        return store(stringRedisTemplate, properties, NexaryObservationPublisher.noop());
    }

    private JobExecutionStore store(
            StringRedisTemplate stringRedisTemplate,
            RedisJobExecutionStoreProperties properties,
            NexaryObservationPublisher publisher) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return new RedisJobExecutionStore(stringRedisTemplate, mapper, properties, publisher);
    }

    private JobExecutionRequest request(
            JobExecutionTrigger trigger,
            int shardIndex,
            int shardTotal,
            JobExecutionPolicy policy) {
        return new JobExecutionRequest(
                null,
                trigger,
                new JobContext("redis-store-job", Instant.now(), shardIndex, shardTotal, null),
                policy);
    }

    private JobExecutionRecord record() {
        Instant now = Instant.now();
        return new JobExecutionRecord(
                JobExecutionId.generate(),
                JobExecutionTrigger.DIRECT,
                new JobContext("redis-store-job", now, 0, 1, null),
                JobExecutionStatus.SUCCESS,
                JobResult.success(),
                1,
                now,
                now,
                Duration.ZERO,
                "ok",
                "");
    }

    private NexaryJob job(ThrowingJob action) {
        return new NexaryJob() {
            @Override
            public String name() {
                return "redis-store-job";
            }

            @Override
            public JobResult execute(JobContext context) throws Exception {
                return action.execute(context);
            }
        };
    }

    private static boolean infraTestsEnabled() {
        return Boolean.parseBoolean(env("NEXARY_RUN_INFRA_TESTS", "false"));
    }

    private static String env(String key, String fallback) {
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static <T> void assertEventually(Supplier<Optional<T>> supplier, Optional<T> expected) {
        AssertionError lastError = null;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            try {
                assertThat(supplier.get()).isEqualTo(expected);
                return;
            } catch (AssertionError ex) {
                lastError = ex;
                sleep(Duration.ofMillis(50));
            }
        }
        throw lastError == null ? new AssertionError("condition did not complete") : lastError;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingJob {
        JobResult execute(JobContext context) throws Exception;
    }

    private static final class RecordingObservationPublisher implements NexaryObservationPublisher {
        private final List<NexaryObservationEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void publish(NexaryObservationEvent event) {
            events.add(event);
        }

        private List<String> operations() {
            return events.stream().map(NexaryObservationEvent::operation).toList();
        }
    }

    private static final class RedisFixture implements AutoCloseable {
        private final LettuceConnectionFactory connectionFactory;
        private final StringRedisTemplate stringRedisTemplate;

        private RedisFixture() {
            RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                    env("NEXARY_INFRA_REDIS_HOST", "127.0.0.1"),
                    Integer.parseInt(env("NEXARY_INFRA_REDIS_PORT", "16379")));
            this.connectionFactory = new LettuceConnectionFactory(configuration);
            this.connectionFactory.afterPropertiesSet();
            this.stringRedisTemplate = new StringRedisTemplate(connectionFactory);
            this.stringRedisTemplate.afterPropertiesSet();
        }

        @Override
        public void close() {
            connectionFactory.destroy();
        }
    }
}
