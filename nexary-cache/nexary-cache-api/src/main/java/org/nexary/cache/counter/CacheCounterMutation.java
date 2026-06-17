package org.nexary.cache.counter;

/** Result of an atomic counter mutation. */
public record CacheCounterMutation(CacheCounterKey key, long value, boolean created, boolean ttlApplied) {
}
