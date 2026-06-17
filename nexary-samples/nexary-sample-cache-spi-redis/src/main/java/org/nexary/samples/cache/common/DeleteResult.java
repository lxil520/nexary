package org.nexary.samples.cache.common;

/** Explicit cache invalidation result. */
public final class DeleteResult {
    private final boolean deleted;

    public DeleteResult(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean deleted() {
        return deleted;
    }
}
