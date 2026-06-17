package org.nexary.cache;

import java.util.Objects;

/** Namespaced cache key. */
public final class CacheKey {
    private final String namespace;
    private final String key;

    public CacheKey(String namespace, String key) {
        this.namespace = requireText(namespace, "namespace");
        this.key = requireText(key, "key");
    }

    /** Creates a cache key. */
    public static CacheKey of(String namespace, String key) {
        return new CacheKey(namespace, key);
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
        if (!(other instanceof CacheKey)) {
            return false;
        }
        CacheKey cacheKey = (CacheKey) other;
        return namespace.equals(cacheKey.namespace) && key.equals(cacheKey.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

    @Override
    public String toString() {
        return "CacheKey[namespace=" + namespace + ", key=" + key + ']';
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
