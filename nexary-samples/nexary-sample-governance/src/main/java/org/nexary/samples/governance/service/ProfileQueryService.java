package org.nexary.samples.governance.service;

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

    public ProfileResult failProfile(String userId) {
        throw new IllegalStateException("demo downstream failure");
    }

    public ProfileResult fallbackProfile(String userId) {
        return new ProfileResult(userId, "Temporary Profile", "fallback");
    }
}
