package org.nexary.samples.cache.common;

/** Batch warmup result. */
public final class WarmupResult {
    private final int size;

    public WarmupResult(int size) {
        this.size = size;
    }

    public int size() {
        return size;
    }
}
