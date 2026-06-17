package org.nexary.samples.cache.common;

import java.io.Serializable;

/** Cached sample profile. */
public final class Profile implements Serializable {
    private final String id;
    private final String name;
    private final String source;

    public Profile(String id, String name, String source) {
        this.id = id;
        this.name = name;
        this.source = source;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String source() {
        return source;
    }
}
