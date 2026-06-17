package org.nexary.samples.cache.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.cache.counter.CacheCounterKey;
import org.nexary.samples.cache.common.DeleteResult;
import org.nexary.samples.cache.common.LockResult;
import org.nexary.samples.cache.common.Profile;
import org.nexary.samples.cache.common.UserCount;
import org.nexary.samples.cache.common.WarmupResult;
import org.springframework.stereotype.Service;

/** User profile use cases that depend only on the Nexary cache API. */
@Service
public class UserProfileService {
    private final CacheClient cacheClient;
    private final CacheCounterClient counterClient;

    public UserProfileService(CacheClient cacheClient, CacheCounterClient counterClient) {
        this.cacheClient = cacheClient;
        this.counterClient = counterClient;
    }

    public Profile profile(String id) {
        return cacheClient.cacheAside(
                CacheKey.of("cache:profile", id),
                Profile.class,
                Duration.ofMinutes(5),
                () -> new Profile(id, "user-" + id, "cache-aside"));
    }

    public WarmupResult warmup() {
        Map<CacheKey, Profile> values = new LinkedHashMap<>();
        values.put(CacheKey.of("cache:profile", "101"), new Profile("101", "batch-101", "putAll"));
        values.put(CacheKey.of("cache:profile", "102"), new Profile("102", "batch-102", "putAll"));
        cacheClient.putAll(values, Duration.ofMinutes(5));
        return new WarmupResult(values.size());
    }

    public Map<String, Object> batch(String ids) {
        List<CacheKey> keys = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(id -> CacheKey.of("cache:profile", id))
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        cacheClient.getAll(keys).forEach((key, value) -> response.put(key.key(), value));
        return response;
    }

    public DeleteResult delete(String id) {
        return new DeleteResult(cacheClient.delete(CacheKey.of("cache:profile", id)));
    }

    public UserCount userCount(String userId) {
        long value = counterClient.current(userCountKey(userId)).orElse(0);
        return new UserCount(userId, value, "current");
    }

    public UserCount incrementUserCount(String userId, long delta) {
        long value = counterClient.increment(userCountKey(userId), delta, Duration.ofMinutes(2)).value();
        return new UserCount(userId, value, "incremented");
    }

    public UserCount decrementUserCount(String userId, long delta) {
        long value = counterClient.decrement(userCountKey(userId), delta, Duration.ofMinutes(2)).value();
        return new UserCount(userId, value, "decremented");
    }

    public DeleteResult deleteUserCount(String userId) {
        return new DeleteResult(counterClient.clear(userCountKey(userId)));
    }

    public LockResult lock(String id) {
        CacheKey key = CacheKey.of("cache:lock", id);
        return cacheClient.tryLock(key, Duration.ofMillis(100), Duration.ofSeconds(10))
                .map(this::useLock)
                .orElseGet(() -> new LockResult(false, false, key.qualified(), null));
    }

    private LockResult useLock(LockHandle lock) {
        try (lock) {
            boolean renewed = lock.renew(Duration.ofSeconds(15));
            Long fencingToken = lock.fencingToken().isPresent() ? lock.fencingToken().getAsLong() : null;
            return new LockResult(true, renewed, lock.key().qualified(), fencingToken);
        }
    }

    private CacheCounterKey userCountKey(String userId) {
        return CacheCounterKey.of("counter:user-count", userId);
    }
}
