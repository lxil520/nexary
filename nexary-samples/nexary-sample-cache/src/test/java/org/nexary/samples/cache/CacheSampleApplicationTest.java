package org.nexary.samples.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.cache.counter.CacheCounterClient;
import org.nexary.cache.counter.CacheCounterKey;
import org.nexary.cache.counter.CacheCounterMutation;
import org.nexary.samples.cache.api.CacheSampleController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest(
        classes = {
                org.nexary.samples.cache.app.CacheSampleApplication.class,
                CacheSampleApplicationTest.TestCacheClientConfiguration.class
        },
        properties = "nexary.cache.provider=none")
class CacheSampleApplicationTest {
    @Autowired
    private CacheSampleController controller;

    @Test
    void cacheSampleExercisesBatchAndCacheAside() {
        assertThat(controller.profile("7").name()).isEqualTo("user-7");
        assertThat(controller.warmup().size()).isEqualTo(2);
        assertThat(controller.batch("101,102")).hasSize(2);
        assertThat(controller.userCount("7").count()).isZero();
        assertThat(controller.incrementUserCount("7", 2).count()).isEqualTo(2);
        assertThat(controller.decrementUserCount("7", 1).count()).isEqualTo(1);
        assertThat(controller.userCount("7").count()).isEqualTo(1);
        assertThat(controller.deleteUserCount("7").deleted()).isTrue();
        assertThat(controller.lock("sample").acquired()).isTrue();
    }

    @TestConfiguration
    static class TestCacheClientConfiguration {
        @Bean
        CacheClient cacheClient() {
            return new InMemoryTestCacheClient();
        }

        @Bean
        CacheCounterClient cacheCounterClient() {
            return new InMemoryTestCounterClient();
        }
    }

    private static final class InMemoryTestCacheClient implements CacheClient {
        private final Map<String, Entry> values = new ConcurrentHashMap<>();
        private final Map<String, String> locks = new ConcurrentHashMap<>();

        @Override
        public <T> Optional<T> get(CacheKey key, Class<T> type) {
            Entry entry = values.get(key.qualified());
            if (entry == null || entry.expired()) {
                values.remove(key.qualified());
                return Optional.empty();
            }
            return Optional.of(type.cast(entry.value()));
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl) {
            values.put(key.qualified(), new Entry(value, Instant.now().plus(normalize(ttl))));
        }

        @Override
        public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
            return values.putIfAbsent(key.qualified(), new Entry(value, Instant.now().plus(normalize(ttl)))) == null;
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
            return values.remove(key.qualified()) != null;
        }

        @Override
        public boolean expire(CacheKey key, Duration ttl) {
            Entry entry = values.get(key.qualified());
            if (entry == null || entry.expired()) {
                return false;
            }
            values.put(key.qualified(), new Entry(entry.value(), Instant.now().plus(normalize(ttl))));
            return true;
        }

        @Override
        public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
            String token = UUID.randomUUID().toString();
            if (locks.putIfAbsent(key.qualified(), token) != null) {
                return Optional.empty();
            }
            return Optional.of(new TestLockHandle(key, token));
        }

        private Duration normalize(Duration ttl) {
            return ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(10) : ttl;
        }

        private record Entry(Object value, Instant expiresAt) {
            private boolean expired() {
                return !Instant.now().isBefore(expiresAt);
            }
        }

        private final class TestLockHandle implements LockHandle {
            private final CacheKey key;
            private final String ownerToken;

            private TestLockHandle(CacheKey key, String ownerToken) {
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
                return ownerToken.equals(locks.get(key.qualified()));
            }

            @Override
            public void close() {
                locks.remove(key.qualified(), ownerToken);
            }
        }
    }

    private static final class InMemoryTestCounterClient implements CacheCounterClient {
        private final Map<String, Long> values = new ConcurrentHashMap<>();

        @Override
        public CacheCounterMutation increment(CacheCounterKey key, long delta, Duration ttlOnCreate) {
            boolean[] created = new boolean[1];
            long value = values.compute(key.qualified(), (ignored, current) -> {
                if (current == null) {
                    created[0] = true;
                    return delta;
                }
                return current + delta;
            });
            return new CacheCounterMutation(key, value, created[0], created[0] && ttlOnCreate != null);
        }

        @Override
        public java.util.OptionalLong current(CacheCounterKey key) {
            Long value = values.get(key.qualified());
            return value == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(value);
        }

        @Override
        public boolean clear(CacheCounterKey key) {
            return values.remove(key.qualified()) != null;
        }
    }
}
