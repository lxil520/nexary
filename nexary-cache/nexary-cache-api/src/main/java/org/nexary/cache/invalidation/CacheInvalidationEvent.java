package org.nexary.cache.invalidation;

import java.time.Instant;
import java.util.Objects;
import org.nexary.cache.CacheKey;

/** Immutable event that asks other nodes to evict a key from their local cache tier. */
public record CacheInvalidationEvent(
        CacheKey key,
        CacheInvalidationOperation operation,
        String originId,
        Instant createdAt) {

    public CacheInvalidationEvent {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        originId = requireText(originId, "originId");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** Creates an invalidation event for a cache mutation. */
    public static CacheInvalidationEvent of(CacheKey key, CacheInvalidationOperation operation, String originId) {
        return new CacheInvalidationEvent(key, operation, originId, Instant.now());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
