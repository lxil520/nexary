package org.nexary.cache.redis.boot2;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.cache.counter.CacheCounterKey;
import org.nexary.cache.counter.CacheCounterMutation;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Spring Data Redis 2.x backed atomic counter implementation for the Boot2 compatibility line. */
public class RedisBoot2CacheCounterClient implements CacheCounterClient {
    private static final DefaultRedisScript<List> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local existed = redis.call('exists', KEYS[1])\n"
                    + "local value = redis.call('incrby', KEYS[1], ARGV[1])\n"
                    + "local ttlApplied = 0\n"
                    + "if existed == 0 and tonumber(ARGV[2]) > 0 then\n"
                    + "    redis.call('pexpire', KEYS[1], ARGV[2])\n"
                    + "    ttlApplied = 1\n"
                    + "end\n"
                    + "return {value, existed == 0 and 1 or 0, ttlApplied}\n",
            List.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final NexaryObservationPublisher observationPublisher;

    public RedisBoot2CacheCounterClient(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, NexaryObservationPublisher.noop());
    }

    public RedisBoot2CacheCounterClient(
            StringRedisTemplate stringRedisTemplate,
            NexaryObservationPublisher observationPublisher) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public CacheCounterMutation increment(CacheCounterKey key, long delta, Duration ttlOnCreate) {
        Instant startedAt = Instant.now();
        String operation = delta < 0 ? "cache.counter_decrement" : "cache.counter_increment";
        try {
            List<?> result = stringRedisTemplate.execute(
                    INCREMENT_SCRIPT,
                    Collections.singletonList(key.qualified()),
                    String.valueOf(delta),
                    String.valueOf(ttlMillis(ttlOnCreate)));
            if (result == null || result.size() < 3) {
                throw new IllegalStateException("Redis counter increment did not return a complete result");
            }
            CacheCounterMutation mutation = new CacheCounterMutation(
                    key,
                    asLong(result.get(0)),
                    asLong(result.get(1)) == 1,
                    asLong(result.get(2)) == 1);
            RedisBoot2CacheObservation.publish(observationPublisher, operation, "none", "success", startedAt);
            return mutation;
        } catch (RuntimeException ex) {
            RedisBoot2CacheObservation.publish(
                    observationPublisher,
                    operation,
                    "none",
                    "failure",
                    RedisBoot2CacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public OptionalLong current(CacheCounterKey key) {
        Instant startedAt = Instant.now();
        try {
            String value = stringRedisTemplate.opsForValue().get(key.qualified());
            if (value == null) {
                RedisBoot2CacheObservation.publish(observationPublisher, "cache.counter_current", "none", "miss", startedAt);
                return OptionalLong.empty();
            }
            RedisBoot2CacheObservation.publish(observationPublisher, "cache.counter_current", "none", "hit", startedAt);
            return OptionalLong.of(Long.parseLong(value));
        } catch (RuntimeException ex) {
            RedisBoot2CacheObservation.publish(
                    observationPublisher,
                    "cache.counter_current",
                    "none",
                    "failure",
                    RedisBoot2CacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public boolean clear(CacheCounterKey key) {
        Instant startedAt = Instant.now();
        try {
            Boolean result = stringRedisTemplate.delete(key.qualified());
            boolean deleted = Boolean.TRUE.equals(result);
            RedisBoot2CacheObservation.publish(
                    observationPublisher, "cache.counter_clear", "none", deleted ? "success" : "miss", startedAt);
            return deleted;
        } catch (RuntimeException ex) {
            RedisBoot2CacheObservation.publish(
                    observationPublisher,
                    "cache.counter_clear",
                    "none",
                    "failure",
                    RedisBoot2CacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    private long ttlMillis(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative() ? 0 : ttl.toMillis();
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
