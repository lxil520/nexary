package org.nexary.cache.counter;

import java.util.Objects;

/** Result of an atomic counter mutation. */
public final class CacheCounterMutation {
    private final CacheCounterKey key;
    private final long value;
    private final boolean created;
    private final boolean ttlApplied;

    public CacheCounterMutation(CacheCounterKey key, long value, boolean created, boolean ttlApplied) {
        this.key = Objects.requireNonNull(key, "key");
        this.value = value;
        this.created = created;
        this.ttlApplied = ttlApplied;
    }

    /** Returns the counter key. */
    public CacheCounterKey key() {
        return key;
    }

    /** Returns the counter value after mutation. */
    public long value() {
        return value;
    }

    /** Returns whether this mutation created the counter. */
    public boolean created() {
        return created;
    }

    /** Returns whether a TTL was applied to the counter. */
    public boolean ttlApplied() {
        return ttlApplied;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CacheCounterMutation)) {
            return false;
        }
        CacheCounterMutation that = (CacheCounterMutation) other;
        return value == that.value
                && created == that.created
                && ttlApplied == that.ttlApplied
                && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, created, ttlApplied);
    }

    @Override
    public String toString() {
        return "CacheCounterMutation[key=" + key
                + ", value=" + value
                + ", created=" + created
                + ", ttlApplied=" + ttlApplied
                + ']';
    }
}
