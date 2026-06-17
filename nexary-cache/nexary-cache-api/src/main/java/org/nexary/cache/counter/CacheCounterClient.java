package org.nexary.cache.counter;

import java.time.Duration;
import java.util.OptionalLong;

/** Provider-neutral atomic counter API that does not use ordinary object cache semantics. */
public interface CacheCounterClient {
    /** Increments a counter by one. */
    default CacheCounterMutation increment(CacheCounterKey key) {
        return increment(key, 1);
    }

    /** Increments a counter by the supplied delta. */
    default CacheCounterMutation increment(CacheCounterKey key, long delta) {
        return increment(key, delta, null);
    }

    /**
     * Increments a counter by the supplied delta.
     *
     * <p>The TTL is applied only when the counter is first created. Later mutations keep the existing TTL and do not
     * refresh it.
     */
    CacheCounterMutation increment(CacheCounterKey key, long delta, Duration ttlOnCreate);

    /** Decrements a counter by one. */
    default CacheCounterMutation decrement(CacheCounterKey key) {
        return decrement(key, 1);
    }

    /** Decrements a counter by the supplied delta. */
    default CacheCounterMutation decrement(CacheCounterKey key, long delta) {
        return decrement(key, delta, null);
    }

    /**
     * Decrements a counter by the supplied delta.
     *
     * <p>The TTL is applied only when the counter is first created. Later mutations keep the existing TTL and do not
     * refresh it.
     */
    default CacheCounterMutation decrement(CacheCounterKey key, long delta, Duration ttlOnCreate) {
        return increment(key, -delta, ttlOnCreate);
    }

    /** Reads the current counter value without creating the counter. */
    OptionalLong current(CacheCounterKey key);

    /** Deletes a counter. */
    boolean clear(CacheCounterKey key);
}
