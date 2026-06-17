package org.nexary.messaging;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** In-memory message deduplication store for local queues and tests. */
public class ConcurrentMapMessageDeduplicationStore implements MessageDeduplicationStore {
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<MessageDeduplicationClaim> claim(String messageId, Duration ttl) {
        Instant expiresAt = Instant.now().plus(normalize(ttl));
        Entry claimed = new Entry(false, expiresAt);
        while (true) {
            Entry current = entries.get(messageId);
            if (current != null && current.expiresAt().isAfter(Instant.now())) {
                return Optional.empty();
            }
            if (current == null) {
                if (entries.putIfAbsent(messageId, claimed) == null) {
                    return Optional.of(new Claim(messageId));
                }
            } else if (entries.replace(messageId, current, claimed)) {
                return Optional.of(new Claim(messageId));
            }
        }
    }

    private Duration normalize(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(30) : ttl;
    }

    private static final class Entry {
        private final boolean consumed;
        private final Instant expiresAt;

        private Entry(boolean consumed, Instant expiresAt) {
            this.consumed = consumed;
            this.expiresAt = expiresAt;
        }

        private boolean consumed() {
            return consumed;
        }

        private Instant expiresAt() {
            return expiresAt;
        }
    }

    private final class Claim implements MessageDeduplicationClaim {
        private final String messageId;
        private boolean completed;

        private Claim(String messageId) {
            this.messageId = messageId;
        }

        @Override
        public String messageId() {
            return messageId;
        }

        @Override
        public void complete() {
            completed = true;
            entries.computeIfPresent(messageId, (key, current) -> new Entry(true, current.expiresAt()));
        }

        @Override
        public void close() {
            if (!completed) {
                entries.remove(messageId);
            }
        }
    }
}
