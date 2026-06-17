package org.nexary.cache.counter;

import java.util.Objects;

/** Namespaced key for atomic counters. */
public record CacheCounterKey(String namespace, String key) {
    public CacheCounterKey {
        namespace = requireText(namespace, "namespace");
        key = requireText(key, "key");
    }

    /** Creates a counter key. */
    public static CacheCounterKey of(String namespace, String key) {
        return new CacheCounterKey(namespace, key);
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
