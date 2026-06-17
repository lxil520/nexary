package org.nexary.cache.counter;

import java.util.Objects;

/** Namespaced key for atomic counters. */
public final class CacheCounterKey {
    private final String namespace;
    private final String key;

    public CacheCounterKey(String namespace, String key) {
        this.namespace = requireText(namespace, "namespace");
        this.key = requireText(key, "key");
    }

    /** Creates a counter key. */
    public static CacheCounterKey of(String namespace, String key) {
        return new CacheCounterKey(namespace, key);
    }

    /** Returns the logical namespace. */
    public String namespace() {
        return namespace;
    }

    /** Returns the logical key inside the namespace. */
    public String key() {
        return key;
    }

    /** Returns the storage key used by implementations. */
    public String qualified() {
        return namespace + ':' + key;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CacheCounterKey)) {
            return false;
        }
        CacheCounterKey that = (CacheCounterKey) other;
        return namespace.equals(that.namespace) && key.equals(that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

    @Override
    public String toString() {
        return "CacheCounterKey[namespace=" + namespace + ", key=" + key + ']';
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
