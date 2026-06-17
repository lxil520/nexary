package org.nexary.samples.cache.common;

/** Cache lock demonstration result. */
public final class LockResult {
    private final boolean acquired;
    private final boolean renewed;
    private final String key;
    private final Long fencingToken;

    public LockResult(boolean acquired, boolean renewed, String key, Long fencingToken) {
        this.acquired = acquired;
        this.renewed = renewed;
        this.key = key;
        this.fencingToken = fencingToken;
    }

    public boolean acquired() {
        return acquired;
    }

    public boolean renewed() {
        return renewed;
    }

    public String key() {
        return key;
    }

    public Long fencingToken() {
        return fencingToken;
    }
}
