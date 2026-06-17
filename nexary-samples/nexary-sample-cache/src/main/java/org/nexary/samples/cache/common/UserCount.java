package org.nexary.samples.cache.common;

import java.io.Serializable;

/** Atomic user counter value. */
public final class UserCount implements Serializable {
    private final String userId;
    private final long count;
    private final String source;

    public UserCount(String userId, long count, String source) {
        this.userId = userId;
        this.count = count;
        this.source = source;
    }

    public String userId() {
        return userId;
    }

    public long count() {
        return count;
    }

    public String source() {
        return source;
    }
}
