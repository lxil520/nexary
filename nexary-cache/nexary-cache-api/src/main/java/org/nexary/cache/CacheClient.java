package org.nexary.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Stable cache API that does not expose a concrete cache provider. */
public interface CacheClient {
    /** Gets a cached value. */
    <T> Optional<T> get(CacheKey key, Class<T> type);

    /** Writes a value with a TTL. */
    void put(CacheKey key, Object value, Duration ttl);

    /** Writes a value only when the key is absent. */
    boolean putIfAbsent(CacheKey key, Object value, Duration ttl);

    /** Gets multiple values. */
    Map<CacheKey, Object> getAll(Collection<CacheKey> keys);

    /** Writes multiple values with a shared TTL. */
    void putAll(Map<CacheKey, ?> values, Duration ttl);

    /** Deletes a value. */
    boolean delete(CacheKey key);

    /** Updates a key TTL. */
    boolean expire(CacheKey key, Duration ttl);

    /** Attempts to acquire a distributed lock. */
    Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime);

    /** Cache-aside helper for common read-through flows. */
    default <T> T cacheAside(CacheKey key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return get(key, type).orElseGet(() -> {
            T loaded = loader.get();
            if (loaded != null) {
                put(key, loaded, ttl);
            }
            return loaded;
        });
    }
}
