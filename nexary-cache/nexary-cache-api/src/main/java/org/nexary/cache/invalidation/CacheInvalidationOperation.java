package org.nexary.cache.invalidation;

/** Cache mutation operation that requires local tier invalidation on other nodes. */
public enum CacheInvalidationOperation {
    /** A value was written to the authoritative tier. */
    PUT,
    /** A value was removed from the authoritative tier. */
    DELETE,
    /** A key TTL changed in the authoritative tier. */
    EXPIRE
}
