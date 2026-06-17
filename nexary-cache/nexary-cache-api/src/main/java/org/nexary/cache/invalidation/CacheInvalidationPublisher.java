package org.nexary.cache.invalidation;

/** Publishes local-tier invalidation events after authoritative cache mutations succeed. */
public interface CacheInvalidationPublisher {
    /** No-op publisher for Redis-only mode or disabled invalidation. */
    CacheInvalidationPublisher NOOP = event -> {
    };

    /** Publishes an invalidation event. */
    void publish(CacheInvalidationEvent event);
}
