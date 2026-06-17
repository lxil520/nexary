package org.nexary.cache.tiered;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;

/** JVM-local TTL cache implementation used as an optional first-level cache. */
public class LocalCacheClient implements CacheClient {
    private final Cache<CacheKey, Entry> values;
    private final ConcurrentMap<CacheKey, LockEntry> locks = new ConcurrentHashMap<>();
    private final Duration defaultTtl;

    public LocalCacheClient(Duration defaultTtl) {
        this.defaultTtl = defaultTtl == null ? Duration.ofSeconds(30) : defaultTtl;
        this.values = Caffeine.newBuilder()
                .expireAfter(new EntryExpiry())
                .build();
    }

    @Override
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        Entry entry = values.getIfPresent(key);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(entry.value()));
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        values.put(key, new Entry(value, expiresAtNanos(ttl)));
    }

    @Override
    public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
        Entry candidate = new Entry(value, expiresAtNanos(ttl));
        return values.asMap().putIfAbsent(key, candidate) == null;
    }

    @Override
    public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
        Map<CacheKey, Object> result = new LinkedHashMap<>();
        for (CacheKey key : keys) {
            get(key, Object.class).ifPresent(value -> result.put(key, value));
        }
        return result;
    }

    @Override
    public void putAll(Map<CacheKey, ?> values, Duration ttl) {
        values.forEach((key, value) -> put(key, value, ttl));
    }

    @Override
    public boolean delete(CacheKey key) {
        Object removed = values.asMap().remove(key);
        return removed != null;
    }

    @Override
    public boolean expire(CacheKey key, Duration ttl) {
        Entry current = values.getIfPresent(key);
        if (current == null) {
            return false;
        }
        values.put(key, new Entry(current.value(), expiresAtNanos(ttl)));
        return true;
    }

    @Override
    public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
        String token = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + normalize(waitTime).toNanos();
        do {
            LockEntry candidate = new LockEntry(token, System.nanoTime() + normalize(leaseTime).toNanos());
            LockEntry current = locks.get(key);
            if (current != null && current.expiresAtNanos() <= System.nanoTime()) {
                locks.remove(key, current);
                current = null;
            }
            if (current == null && locks.putIfAbsent(key, candidate) == null) {
                return Optional.of(new LocalLockHandle(key, token));
            }
            sleep(Duration.ofMillis(10));
        } while (System.nanoTime() < deadline);
        return Optional.empty();
    }

    private Duration normalize(Duration ttl) {
        return ttl == null || ttl.isZero() || ttl.isNegative() ? defaultTtl : ttl;
    }

    private long expiresAtNanos(Duration ttl) {
        return System.nanoTime() + normalize(ttl).toNanos();
    }

    private void sleep(Duration interval) {
        try {
            Thread.sleep(Math.max(1L, interval.toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Entry {
        private final Object value;
        private final long expiresAtNanos;

        private Entry(Object value, long expiresAtNanos) {
            this.value = value;
            this.expiresAtNanos = expiresAtNanos;
        }

        private Object value() {
            return value;
        }

        private long remainingNanos(long currentTimeNanos) {
            long remaining = expiresAtNanos - currentTimeNanos;
            return remaining > 0 ? remaining : 1L;
        }
    }

    private final class LocalLockHandle implements LockHandle {
        private final CacheKey key;
        private final String ownerToken;

        private LocalLockHandle(CacheKey key, String ownerToken) {
            this.key = key;
            this.ownerToken = ownerToken;
        }

        @Override
        public CacheKey key() {
            return key;
        }

        @Override
        public String ownerToken() {
            return ownerToken;
        }

        @Override
        public boolean renew(Duration leaseTime) {
            LockEntry current = locks.get(key);
            if (current == null || !ownerToken.equals(current.ownerToken()) || current.expiresAtNanos() <= System.nanoTime()) {
                return false;
            }
            locks.put(key, new LockEntry(ownerToken, System.nanoTime() + normalize(leaseTime).toNanos()));
            return true;
        }

        @Override
        public void close() {
            locks.computeIfPresent(key, (ignored, current) -> ownerToken.equals(current.ownerToken()) ? null : current);
        }
    }

    private static final class LockEntry {
        private final String ownerToken;
        private final long expiresAtNanos;

        private LockEntry(String ownerToken, long expiresAtNanos) {
            this.ownerToken = ownerToken;
            this.expiresAtNanos = expiresAtNanos;
        }

        private String ownerToken() {
            return ownerToken;
        }

        private long expiresAtNanos() {
            return expiresAtNanos;
        }
    }

    private static final class EntryExpiry implements Expiry<CacheKey, Entry> {
        @Override
        public long expireAfterCreate(CacheKey key, Entry value, long currentTime) {
            return value.remainingNanos(currentTime);
        }

        @Override
        public long expireAfterUpdate(CacheKey key, Entry value, long currentTime, long currentDuration) {
            return value.remainingNanos(currentTime);
        }

        @Override
        public long expireAfterRead(CacheKey key, Entry value, long currentTime, long currentDuration) {
            return currentDuration;
        }
    }
}
