package org.nexary.cache.tiered;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.cache.invalidation.CacheInvalidationEvent;
import org.nexary.cache.invalidation.CacheInvalidationListener;
import org.nexary.cache.invalidation.CacheInvalidationOperation;
import org.nexary.cache.invalidation.CacheInvalidationPublisher;
import org.nexary.core.observation.NexaryObservationPublisher;

/** Two-level cache that combines a fast local tier with a remote authoritative tier. */
public class TieredCacheClient implements CacheClient, CacheInvalidationListener {
    private final CacheClient local;
    private final CacheClient remote;
    private final Duration localTtl;
    private final CacheInvalidationPublisher invalidationPublisher;
    private final String originId;
    private final NexaryObservationPublisher observationPublisher;

    public TieredCacheClient(CacheClient local, CacheClient remote, Duration localTtl) {
        this(local, remote, localTtl, CacheInvalidationPublisher.NOOP, "local");
    }

    public TieredCacheClient(
            CacheClient local,
            CacheClient remote,
            Duration localTtl,
            CacheInvalidationPublisher invalidationPublisher,
            String originId) {
        this(local, remote, localTtl, invalidationPublisher, originId, NexaryObservationPublisher.noop());
    }

    public TieredCacheClient(
            CacheClient local,
            CacheClient remote,
            Duration localTtl,
            CacheInvalidationPublisher invalidationPublisher,
            String originId,
            NexaryObservationPublisher observationPublisher) {
        this.local = local;
        this.remote = remote;
        this.localTtl = localTtl == null ? Duration.ofSeconds(30) : localTtl;
        this.invalidationPublisher = invalidationPublisher == null ? CacheInvalidationPublisher.NOOP : invalidationPublisher;
        this.originId = originId == null || originId.isBlank() ? "local" : originId;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        Instant startedAt = Instant.now();
        try {
            Optional<T> localValue = local.get(key, type);
            if (localValue.isPresent()) {
                TieredCacheObservation.publish(observationPublisher, "cache.get", "l1", "hit", startedAt);
                return localValue;
            }
            TieredCacheObservation.publish(observationPublisher, "cache.get", "l1", "miss", startedAt);
            Instant remoteStartedAt = Instant.now();
            Optional<T> remoteValue = remote.get(key, type);
            TieredCacheObservation.publish(
                    observationPublisher, "cache.get", "l2", remoteValue.isPresent() ? "hit" : "miss", remoteStartedAt);
            remoteValue.ifPresent(value -> local.put(key, value, localTtl));
            return remoteValue;
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.get",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            remote.put(key, value, ttl);
            local.put(key, value, shorter(ttl));
            publish(key, CacheInvalidationOperation.PUT);
            TieredCacheObservation.publish(observationPublisher, "cache.put", "none", "success", startedAt);
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.put",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            boolean written = remote.putIfAbsent(key, value, ttl);
            if (written) {
                local.put(key, value, shorter(ttl));
                publish(key, CacheInvalidationOperation.PUT);
            }
            TieredCacheObservation.publish(
                    observationPublisher, "cache.put_if_absent", "none", written ? "success" : "not_stored", startedAt);
            return written;
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.put_if_absent",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
        Instant startedAt = Instant.now();
        try {
            Map<CacheKey, Object> result = new LinkedHashMap<>(local.getAll(keys));
            TieredCacheObservation.publish(
                    observationPublisher, "cache.batch_get", "l1", result.size() == keys.size() ? "hit" : "miss", startedAt);
            var misses = keys.stream().filter(key -> !result.containsKey(key)).toList();
            Instant remoteStartedAt = Instant.now();
            Map<CacheKey, Object> remoteValues = remote.getAll(misses);
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.batch_get",
                    "l2",
                    remoteValues.isEmpty() ? "miss" : "hit",
                    remoteStartedAt);
            remoteValues.forEach((key, value) -> local.put(key, value, localTtl));
            result.putAll(remoteValues);
            return result;
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.batch_get",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public void putAll(Map<CacheKey, ?> values, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            remote.putAll(values, ttl);
            local.putAll(values, shorter(ttl));
            values.keySet().forEach(key -> publish(key, CacheInvalidationOperation.PUT));
            TieredCacheObservation.publish(observationPublisher, "cache.batch_put", "none", "success", startedAt);
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.batch_put",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public boolean delete(CacheKey key) {
        Instant startedAt = Instant.now();
        try {
            boolean remoteDeleted = remote.delete(key);
            boolean localDeleted = local.delete(key);
            if (remoteDeleted) {
                publish(key, CacheInvalidationOperation.DELETE);
            }
            boolean deleted = remoteDeleted || localDeleted;
            TieredCacheObservation.publish(
                    observationPublisher, "cache.delete", "none", deleted ? "success" : "miss", startedAt);
            return deleted;
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.delete",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public boolean expire(CacheKey key, Duration ttl) {
        Instant startedAt = Instant.now();
        try {
            boolean remoteExpired = remote.expire(key, ttl);
            boolean localExpired = local.expire(key, shorter(ttl));
            if (remoteExpired) {
                publish(key, CacheInvalidationOperation.EXPIRE);
            }
            boolean expired = remoteExpired || localExpired;
            TieredCacheObservation.publish(
                    observationPublisher, "cache.expire", "none", expired ? "success" : "miss", startedAt);
            return expired;
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.expire",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
        return remote.tryLock(key, waitTime, leaseTime);
    }

    @Override
    public <T> T cacheAside(CacheKey key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return get(key, type).orElseGet(() -> {
            T loaded = loader.get();
            if (loaded != null) {
                put(key, loaded, ttl);
            }
            return loaded;
        });
    }

    @Override
    public void onInvalidation(CacheInvalidationEvent event) {
        Instant startedAt = Instant.now();
        boolean evicted = local.delete(event.key());
        TieredCacheObservation.publish(
                observationPublisher, "cache.invalidation_evict", "l1", evicted ? "evicted" : "miss", startedAt);
    }

    private void publish(CacheKey key, CacheInvalidationOperation operation) {
        Instant startedAt = Instant.now();
        try {
            invalidationPublisher.publish(CacheInvalidationEvent.of(key, operation, originId));
            TieredCacheObservation.publish(
                    observationPublisher, "cache.invalidation_publish", "none", "published", startedAt);
        } catch (RuntimeException ex) {
            TieredCacheObservation.publish(
                    observationPublisher,
                    "cache.invalidation_publish",
                    "none",
                    "failure",
                    TieredCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    private Duration shorter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return localTtl;
        }
        return ttl.compareTo(localTtl) < 0 ? ttl : localTtl;
    }
}
