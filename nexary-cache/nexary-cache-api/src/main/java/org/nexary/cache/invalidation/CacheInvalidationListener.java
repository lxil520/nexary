package org.nexary.cache.invalidation;

/** Receives invalidation events and evicts matching local cache entries. */
public interface CacheInvalidationListener {
    /** Handles one invalidation event. */
    void onInvalidation(CacheInvalidationEvent event);
}
