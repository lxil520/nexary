package org.nexary.cache.counter;

import java.util.Objects;

/** Current value of an atomic counter. */
public final class CacheCounterValue {
    private final CacheCounterKey key;
    private final long value;

    public CacheCounterValue(CacheCounterKey key, long value) {
        this.key = Objects.requireNonNull(key, "key");
        this.value = value;
    }

    /** Returns the counter key. */
    public CacheCounterKey key() {
        return key;
    }

    /** Returns the current counter value. */
    public long value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CacheCounterValue)) {
            return false;
        }
        CacheCounterValue that = (CacheCounterValue) other;
        return value == that.value && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "CacheCounterValue[key=" + key + ", value=" + value + ']';
    }
}
