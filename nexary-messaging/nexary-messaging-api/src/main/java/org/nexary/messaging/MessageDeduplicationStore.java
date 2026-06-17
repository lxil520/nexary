package org.nexary.messaging;

import java.time.Duration;
import java.util.Optional;

/** Stores provider-neutral message consume claims for duplicate consumption protection. */
public interface MessageDeduplicationStore {
    /** Attempts to claim a message id for consumption. */
    Optional<MessageDeduplicationClaim> claim(String messageId, Duration ttl);
}
