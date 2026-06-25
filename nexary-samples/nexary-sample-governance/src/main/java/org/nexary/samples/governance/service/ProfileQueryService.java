package org.nexary.samples.governance.service;

import java.time.Duration;
import org.nexary.core.context.CancellationContext;
import org.nexary.samples.governance.common.ProfileResult;
import org.springframework.stereotype.Service;

/** Business service used by the governance sample. */
@Service
public class ProfileQueryService {
    public ProfileResult loadProfile(String userId) {
        return new ProfileResult(userId, "Demo User " + userId, "primary");
    }

    public ProfileResult slowProfile(String userId) throws InterruptedException {
        Thread.sleep(150);
        return new ProfileResult(userId, "Demo User " + userId, "slow-primary");
    }

    public ProfileResult cancellableSlowProfile(String userId, Duration maxDuration) throws InterruptedException {
        long deadlineNanos = System.nanoTime() + maxDuration.toNanos();
        while (System.nanoTime() < deadlineNanos) {
            if (CancellationContext.cancelled()) {
                return new ProfileResult(userId, "Cancelled Profile", "cancelled");
            }
            Thread.sleep(25);
        }
        return new ProfileResult(userId, "Demo User " + userId, "slow-primary");
    }

    public ProfileResult failProfile(String userId) {
        throw new IllegalStateException("demo downstream failure");
    }

    public ProfileResult fallbackProfile(String userId) {
        return new ProfileResult(userId, "Temporary Profile", "fallback");
    }
}
