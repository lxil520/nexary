package org.nexary.cache;

import java.time.Duration;
import java.util.OptionalLong;

/** Handle returned by a distributed lock attempt. */
public interface LockHandle extends AutoCloseable {
    /** Lock key. */
    CacheKey key();

    /** Unique owner token. */
    String ownerToken();

    /**
     * Optional fencing token issued for this lock acquisition.
     *
     * <p>When present, callers should carry this token to the protected resource and let that resource reject stale
     * operations whose token is lower than the highest token it has already accepted.
     */
    default OptionalLong fencingToken() {
        return OptionalLong.empty();
    }

    /** Renews the lease for the current owner. */
    boolean renew(Duration leaseTime);

    /** Releases the lock. */
    @Override
    void close();
}
