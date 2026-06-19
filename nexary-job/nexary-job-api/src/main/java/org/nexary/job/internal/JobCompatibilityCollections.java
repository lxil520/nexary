package org.nexary.job.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Internal collection helpers that avoid newer JDK factory APIs in job main sources. */
public final class JobCompatibilityCollections {
    private JobCompatibilityCollections() {
    }

    /** Returns an immutable empty list. */
    public static <T> List<T> emptyList() {
        return Collections.emptyList();
    }

    /** Returns an immutable defensive copy. */
    public static <T> List<T> copyList(List<T> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    /** Collects a stream into an immutable list without newer JDK stream APIs. */
    public static <T> List<T> collectList(Stream<T> stream) {
        if (stream == null) {
            return Collections.emptyList();
        }
        ArrayList<T> values = stream.collect(Collectors.toCollection(ArrayList::new));
        return Collections.unmodifiableList(values);
    }

    /** Returns an immutable defensive copy of a string map. */
    public static Map<String, String> copyMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    /** Returns a single-entry immutable map. */
    public static Map<String, String> tags(String key, String value) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(key, value);
        return Collections.unmodifiableMap(tags);
    }

    /** Returns a two-entry immutable map. */
    public static Map<String, String> tags(String key1, String value1, String key2, String value2) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(key1, value1);
        tags.put(key2, value2);
        return Collections.unmodifiableMap(tags);
    }

    /** Returns a three-entry immutable map. */
    public static Map<String, String> tags(
            String key1,
            String value1,
            String key2,
            String value2,
            String key3,
            String value3) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put(key1, value1);
        tags.put(key2, value2);
        tags.put(key3, value3);
        return Collections.unmodifiableMap(tags);
    }
}
