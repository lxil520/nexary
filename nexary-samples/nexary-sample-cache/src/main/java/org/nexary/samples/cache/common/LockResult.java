package org.nexary.samples.cache.common;

/** Cache lock demonstration result. */
public record LockResult(boolean acquired, boolean renewed, String key, Long fencingToken) {
}
