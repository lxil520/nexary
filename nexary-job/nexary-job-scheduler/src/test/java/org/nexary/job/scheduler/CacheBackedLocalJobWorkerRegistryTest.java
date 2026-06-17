package org.nexary.job.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.job.JobSchedule;

class CacheBackedLocalJobWorkerRegistryTest {
    @Test
    void registersCurrentWorkerThroughHeartbeat() {
        InMemoryCacheClient cacheClient = new InMemoryCacheClient();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        properties.setWorkerId("node-a");
        properties.setTopology("test");
        CacheBackedLocalJobWorkerRegistry registry =
                new CacheBackedLocalJobWorkerRegistry(Optional.of(cacheClient), properties);

        registry.heartbeat();

        assertThat(registry.workerIds(new JobSchedule("sample-job", "0 */10 * * * *", false, 2)))
                .containsExactly("node-a");
    }

    @Test
    void removesExpiredWorkersFromTopology() throws Exception {
        InMemoryCacheClient cacheClient = new InMemoryCacheClient();
        LocalJobSchedulerProperties properties = new LocalJobSchedulerProperties();
        properties.setWorkerId("node-a");
        properties.setTopology("test-expire");
        properties.setHeartbeatTtl(Duration.ofMillis(20));
        properties.setHeartbeatInterval(Duration.ofMillis(5));
        CacheBackedLocalJobWorkerRegistry nodeA =
                new CacheBackedLocalJobWorkerRegistry(Optional.of(cacheClient), properties);
        nodeA.heartbeat();

        LocalJobSchedulerProperties nodeBProperties = new LocalJobSchedulerProperties();
        nodeBProperties.setWorkerId("node-b");
        nodeBProperties.setTopology("test-expire");
        nodeBProperties.setHeartbeatTtl(Duration.ofMillis(20));
        nodeBProperties.setHeartbeatInterval(Duration.ofMillis(5));
        CacheBackedLocalJobWorkerRegistry nodeB =
                new CacheBackedLocalJobWorkerRegistry(Optional.of(cacheClient), nodeBProperties);
        Thread.sleep(30);
        nodeB.heartbeat();

        assertThat(nodeB.workerIds(new JobSchedule("sample-job", "0 */10 * * * *", false, 2)))
                .containsExactly("node-b");
    }

    private static final class InMemoryCacheClient implements CacheClient {
        private final Map<CacheKey, Object> values = new ConcurrentHashMap<>();
        private final Map<CacheKey, String> locks = new ConcurrentHashMap<>();

        @Override
        public <T> Optional<T> get(CacheKey key, Class<T> type) {
            Object value = values.get(key);
            return value == null ? Optional.empty() : Optional.of(type.cast(value));
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl) {
            values.put(key, value);
        }

        @Override
        public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
            return values.putIfAbsent(key, value) == null;
        }

        @Override
        public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
            Map<CacheKey, Object> result = new LinkedHashMap<>();
            keys.forEach(key -> {
                Object value = values.get(key);
                if (value != null) {
                    result.put(key, value);
                }
            });
            return result;
        }

        @Override
        public void putAll(Map<CacheKey, ?> values, Duration ttl) {
            this.values.putAll(values);
        }

        @Override
        public boolean delete(CacheKey key) {
            return values.remove(key) != null;
        }

        @Override
        public boolean expire(CacheKey key, Duration ttl) {
            return values.containsKey(key);
        }

        @Override
        public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
            String token = UUID.randomUUID().toString();
            return locks.putIfAbsent(key, token) == null ? Optional.of(new InMemoryLockHandle(key, token)) : Optional.empty();
        }

        @Override
        public <T> T cacheAside(CacheKey key, Class<T> type, Duration ttl, Supplier<T> loader) {
            return CacheClient.super.cacheAside(key, type, ttl, loader);
        }

        private final class InMemoryLockHandle implements LockHandle {
            private final CacheKey key;
            private final String token;

            private InMemoryLockHandle(CacheKey key, String token) {
                this.key = key;
                this.token = token;
            }

            @Override
            public CacheKey key() {
                return key;
            }

            @Override
            public String ownerToken() {
                return token;
            }

            @Override
            public boolean renew(Duration leaseTime) {
                return token.equals(locks.get(key));
            }

            @Override
            public void close() {
                locks.remove(key, token);
            }
        }
    }
}
