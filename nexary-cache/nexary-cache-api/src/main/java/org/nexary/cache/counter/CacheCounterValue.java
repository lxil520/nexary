package org.nexary.cache.counter;

/** Current value of an atomic counter. */
public record CacheCounterValue(CacheCounterKey key, long value) {
}
