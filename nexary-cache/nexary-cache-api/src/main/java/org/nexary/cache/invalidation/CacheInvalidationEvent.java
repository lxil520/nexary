package org.nexary.cache.invalidation;

import java.time.Instant;
import java.util.Objects;
import org.nexary.cache.CacheKey;

/** Immutable event that asks other nodes to evict a key from their local cache tier. */
public final class CacheInvalidationEvent {
    private final CacheKey key;
    private final CacheInvalidationOperation operation;
    private final String originId;
    private final Instant createdAt;

    public CacheInvalidationEvent(
            CacheKey key,
            CacheInvalidationOperation operation,
            String originId,
            Instant createdAt) {
        this.key = Objects.requireNonNull(key, "key");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.originId = requireText(originId, "originId");
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** Creates an invalidation event for a cache mutation. */
    public static CacheInvalidationEvent of(CacheKey key, CacheInvalidationOperation operation, String originId) {
        return new CacheInvalidationEvent(key, operation, originId, Instant.now());
    }

    /** Returns the invalidated cache key. */
    public CacheKey key() {
        return key;
    }

    /** Returns the cache mutation operation. */
    public CacheInvalidationOperation operation() {
        return operation;
    }

    /** Returns the producer node id. */
    public String originId() {
        return originId;
    }

    /** Returns event creation time. */
    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CacheInvalidationEvent)) {
            return false;
        }
        CacheInvalidationEvent that = (CacheInvalidationEvent) other;
        return key.equals(that.key)
                && operation == that.operation
                && originId.equals(that.originId)
                && createdAt.equals(that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, operation, originId, createdAt);
    }

    @Override
    public String toString() {
        return "CacheInvalidationEvent[key=" + key
                + ", operation=" + operation
                + ", originId=" + originId
                + ", createdAt=" + createdAt
                + ']';
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
