package org.nexary.cache.redis;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import static java.util.stream.Collectors.toList;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/** Redis-backed CacheClient implementation. */
public class RedisCacheClient implements CacheClient {
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> LOCK_WITH_FENCE_SCRIPT = new DefaultRedisScript<>(
            "local locked = redis.call('set', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2])\n"
                    + "if locked then\n"
                    + "    return redis.call('incr', KEYS[2])\n"
                    + "end\n"
                    + "return 0\n",
            Long.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisCacheProperties properties;
    private final NexaryObservationPublisher observationPublisher;

    public RedisCacheClient(
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisCacheProperties properties) {
        this(redisTemplate, stringRedisTemplate, properties, NexaryObservationPublisher.noop());
    }

    public RedisCacheClient(
            RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate stringRedisTemplate,
            RedisCacheProperties properties,
            NexaryObservationPublisher observationPublisher) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        Instant startedAt = Instant.now();
        try {
            Object value = redisTemplate.opsForValue().get(key.qualified());
            if (value == null) {
                publish("cache.get", "l2", "miss", startedAt);
                return Optional.empty();
            }
            publish("cache.get", "l2", "hit", startedAt);
            return Optional.of(type.cast(value));
        } catch (RuntimeException ex) {
            publish("cache.get", "l2", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            redisTemplate.opsForValue().set(key.qualified(), value, normalizeTtl(ttl));
            publish("cache.put", "l2", "success", startedAt);
        } catch (RuntimeException ex) {
            publish("cache.put", "l2", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    @Override
    public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key.qualified(), value, normalizeTtl(ttl));
            boolean written = Boolean.TRUE.equals(result);
            publish("cache.put_if_absent", "l2", written ? "success" : "not_stored", startedAt);
            return written;
        } catch (RuntimeException ex) {
            publish("cache.put_if_absent", "l2", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    @Override
    public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
        Instant startedAt = Instant.now();
        try {
            List<String> redisKeys = keys.stream().map(CacheKey::qualified).collect(toList());
            List<Object> values = redisTemplate.opsForValue().multiGet(redisKeys);
            Map<CacheKey, Object> result = new LinkedHashMap<>();
            int index = 0;
            for (CacheKey key : keys) {
                Object value = values == null || index >= values.size() ? null : values.get(index);
                if (value != null) {
                    result.put(key, value);
                }
                index++;
            }
            publish("cache.batch_get", "l2", result.isEmpty() ? "miss" : "hit", startedAt);
            return result;
        } catch (RuntimeException ex) {
            publish("cache.batch_get", "l2", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    @Override
    public void putAll(Map<CacheKey, ?> values, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            values.forEach((key, value) -> redisTemplate.opsForValue().set(key.qualified(), value, normalizeTtl(ttl)));
            publish("cache.batch_put", "l2", "success", startedAt);
        } catch (RuntimeException ex) {
            publish("cache.batch_put", "l2", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    @Override
    public boolean delete(CacheKey key) {
        Instant startedAt = Instant.now();
        try {
            Boolean result = redisTemplate.delete(key.qualified());
            boolean deleted = Boolean.TRUE.equals(result);
            publish("cache.delete", "l2", deleted ? "success" : "miss", startedAt);
            return deleted;
        } catch (RuntimeException ex) {
            publish("cache.delete", "l2", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    @Override
    public boolean expire(CacheKey key, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            Boolean result = redisTemplate.expire(key.qualified(), normalizeTtl(ttl));
            boolean expired = Boolean.TRUE.equals(result);
            publish("cache.expire", "l2", expired ? "success" : "miss", startedAt);
            return expired;
        } catch (RuntimeException ex) {
            publish("cache.expire", "l2", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    @Override
    public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
        Instant startedAt = Instant.now();
        String lockKey = properties.getLockPrefix() + key.qualified();
        String fencingKey = properties.getFencingTokenPrefix() + key.qualified();
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + normalizeWaitTime(waitTime).toNanos();
        try {
            do {
                Long fencingToken = stringRedisTemplate.execute(
                        LOCK_WITH_FENCE_SCRIPT,
                        Arrays.asList(lockKey, fencingKey),
                        token,
                        String.valueOf(normalizeTtl(leaseTime).toMillis()));
                if (fencingToken != null && fencingToken > 0) {
                    publish("cache.lock_acquire", "none", "acquired", startedAt);
                    return Optional.of(new RedisLockHandle(key, lockKey, token, fencingToken));
                }
                sleep(properties.getLockRetryInterval());
            } while (System.nanoTime() < deadline);
            publish("cache.lock_acquire", "none", "not_acquired", startedAt);
            return Optional.empty();
        } catch (RuntimeException ex) {
            publish("cache.lock_acquire", "none", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
            throw ex;
        }
    }

    private Duration normalizeTtl(Duration ttl) {
        return ttl == null || ttl.isNegative() || ttl.isZero() ? properties.getDefaultTtl() : ttl;
    }

    private Duration normalizeWaitTime(Duration waitTime) {
        return waitTime == null || waitTime.isNegative() ? Duration.ZERO : waitTime;
    }

    private void sleep(Duration interval) {
        try {
            Thread.sleep(Math.max(1L, interval.toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void publish(String operation, String tier, String outcome, Instant startedAt) {
        RedisCacheObservation.publishForProvider(
                observationPublisher, properties.getProviderName(), operation, tier, outcome, startedAt);
    }

    private void publish(String operation, String tier, String outcome, String failure, Instant startedAt) {
        RedisCacheObservation.publishForProvider(
                observationPublisher, properties.getProviderName(), operation, tier, outcome, failure, startedAt);
    }

    private final class RedisLockHandle implements LockHandle {
        private final CacheKey key;
        private final String redisKey;
        private final String ownerToken;
        private final long fencingToken;

        private RedisLockHandle(CacheKey key, String redisKey, String ownerToken, long fencingToken) {
            this.key = key;
            this.redisKey = redisKey;
            this.ownerToken = ownerToken;
            this.fencingToken = fencingToken;
        }

        @Override
        public CacheKey key() {
            return key;
        }

        @Override
        public String ownerToken() {
            return ownerToken;
        }

        @Override
        public OptionalLong fencingToken() {
            return OptionalLong.of(fencingToken);
        }

        @Override
        public boolean renew(Duration leaseTime) {
            Instant startedAt = Instant.now();
            try {
                Long result = stringRedisTemplate.execute(
                        RENEW_SCRIPT,
                        Collections.singletonList(redisKey),
                        ownerToken,
                        String.valueOf(normalizeTtl(leaseTime).toMillis()));
                boolean renewed = result != null && result > 0;
                publish("cache.lock_renew", "none", renewed ? "success" : "not_owner", startedAt);
                return renewed;
            } catch (RuntimeException ex) {
                publish("cache.lock_renew", "none", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
                throw ex;
            }
        }

        @Override
        public void close() {
            Instant startedAt = Instant.now();
            try {
                Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(redisKey), ownerToken);
                publish(
                        "cache.lock_release",
                        "none",
                        result != null && result > 0 ? "success" : "not_owner",
                        startedAt);
            } catch (RuntimeException ex) {
                publish("cache.lock_release", "none", "failure", RedisCacheObservation.failureCategory(ex), startedAt);
                throw ex;
            }
        }
    }
}
