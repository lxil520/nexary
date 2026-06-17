package org.nexary.cache;

import java.util.Objects;

/** Namespaced cache key. */
public record CacheKey(String namespace, String key) {
    public CacheKey {
        namespace = requireText(namespace, "namespace");
        key = requireText(key, "key");
    }

    /** Creates a cache key. */
    public static CacheKey of(String namespace, String key) {
        return new CacheKey(namespace, key);
    }

    /** Returns the storage key used by implementations. */
    public String qualified() {
        return namespace + ':' + key;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
