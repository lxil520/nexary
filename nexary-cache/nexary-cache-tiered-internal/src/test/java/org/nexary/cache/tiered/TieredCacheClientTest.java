package org.nexary.cache.tiered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.cache.invalidation.CacheInvalidationEvent;
import org.nexary.cache.invalidation.CacheInvalidationOperation;
import org.nexary.cache.invalidation.CacheInvalidationPublisher;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;

class TieredCacheClientTest {
    @Test
    void backfillsLocalTierFromRemote() {
        LocalCacheClient local = new LocalCacheClient(Duration.ofSeconds(5));
        LocalCacheClient remote = new LocalCacheClient(Duration.ofMinutes(5));
        TieredCacheClient tiered = new TieredCacheClient(local, remote, Duration.ofSeconds(5));
        CacheKey key = CacheKey.of("user", "42");

        remote.put(key, "Alice", Duration.ofMinutes(1));

        assertThat(tiered.get(key, String.class)).contains("Alice");
        remote.delete(key);
        assertThat(tiered.get(key, String.class)).contains("Alice");
    }

    @Test
    void cacheAsideWritesBothTiersOnce() {
        TieredCacheClient tiered = new TieredCacheClient(
                new LocalCacheClient(Duration.ofSeconds(5)),
                new LocalCacheClient(Duration.ofMinutes(5)),
                Duration.ofSeconds(5));
        AtomicInteger loads = new AtomicInteger();
        CacheKey key = CacheKey.of("user", "43");

        String first = tiered.cacheAside(key, String.class, Duration.ofMinutes(1), () -> "load-" + loads.incrementAndGet());
        String second = tiered.cacheAside(key, String.class, Duration.ofMinutes(1), () -> "load-" + loads.incrementAndGet());

        assertThat(first).isEqualTo("load-1");
        assertThat(second).isEqualTo("load-1");
        assertThat(loads).hasValue(1);
    }

    @Test
    void localTierCanServeStaleValueWrittenByAnotherNodeUntilLocalTtlExpires() {
        LocalCacheClient remote = new LocalCacheClient(Duration.ofMinutes(5));
        TieredCacheClient nodeA = new TieredCacheClient(
                new LocalCacheClient(Duration.ofSeconds(30)), remote, Duration.ofSeconds(30));
        TieredCacheClient nodeB = new TieredCacheClient(
                new LocalCacheClient(Duration.ofSeconds(30)), remote, Duration.ofSeconds(30));
        CacheKey key = CacheKey.of("user-count", "42");

        nodeA.put(key, 1, Duration.ofMinutes(1));
        assertThat(nodeB.get(key, Integer.class)).contains(1);

        nodeA.put(key, 2, Duration.ofMinutes(1));

        assertThat(nodeB.get(key, Integer.class)).contains(1);
    }

    @Test
    void publishesInvalidationAfterSuccessfulRemoteMutation() {
        RecordingPublisher publisher = new RecordingPublisher();
        TieredCacheClient tiered = new TieredCacheClient(
                new LocalCacheClient(Duration.ofSeconds(30)),
                new LocalCacheClient(Duration.ofMinutes(5)),
                Duration.ofSeconds(30),
                publisher,
                "node-a");
        CacheKey key = CacheKey.of("user-count", "42");

        tiered.put(key, 2, Duration.ofMinutes(1));
        tiered.expire(key, Duration.ofMinutes(2));
        tiered.delete(key);

        assertThat(publisher.events)
                .extracting(CacheInvalidationEvent::operation)
                .containsExactly(
                        CacheInvalidationOperation.PUT,
                        CacheInvalidationOperation.EXPIRE,
                        CacheInvalidationOperation.DELETE);
        assertThat(publisher.events).allSatisfy(event -> assertThat(event.originId()).isEqualTo("node-a"));
    }

    @Test
    void doesNotPublishInvalidationWhenRemoteMutationFails() {
        RecordingPublisher publisher = new RecordingPublisher();
        TieredCacheClient tiered = new TieredCacheClient(
                new LocalCacheClient(Duration.ofSeconds(30)),
                new FailingCacheClient(),
                Duration.ofSeconds(30),
                publisher,
                "node-a");

        assertThatThrownBy(() -> tiered.put(CacheKey.of("user-count", "42"), 2, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tiered.delete(CacheKey.of("user-count", "42")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tiered.expire(CacheKey.of("user-count", "42"), Duration.ofMinutes(1)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(publisher.events).isEmpty();
    }

    @Test
    void receivedInvalidationEvictsMatchingLocalKeyOnly() {
        LocalCacheClient remote = new LocalCacheClient(Duration.ofMinutes(5));
        TieredCacheClient node = new TieredCacheClient(
                new LocalCacheClient(Duration.ofMinutes(5)), remote, Duration.ofMinutes(5));
        CacheKey changed = CacheKey.of("user-count", "42");
        CacheKey untouched = CacheKey.of("user-count", "43");

        remote.put(changed, 1, Duration.ofMinutes(1));
        remote.put(untouched, 10, Duration.ofMinutes(1));
        assertThat(node.get(changed, Integer.class)).contains(1);
        assertThat(node.get(untouched, Integer.class)).contains(10);
        remote.put(changed, 2, Duration.ofMinutes(1));
        remote.put(untouched, 11, Duration.ofMinutes(1));

        node.onInvalidation(CacheInvalidationEvent.of(changed, CacheInvalidationOperation.PUT, "node-a"));

        assertThat(node.get(changed, Integer.class)).contains(2);
        assertThat(node.get(untouched, Integer.class)).contains(10);
    }

    @Test
    void emitsTieredHitMissAndInvalidationEventsWithBoundedTags() {
        RecordingObservationPublisher observationPublisher = new RecordingObservationPublisher();
        RecordingPublisher invalidationPublisher = new RecordingPublisher();
        LocalCacheClient remote = new LocalCacheClient(Duration.ofMinutes(5));
        TieredCacheClient tiered = new TieredCacheClient(
                new LocalCacheClient(Duration.ofSeconds(30)),
                remote,
                Duration.ofSeconds(30),
                invalidationPublisher,
                "node-a",
                observationPublisher);
        CacheKey key = CacheKey.of("user", "42");

        assertThat(tiered.get(key, String.class)).isEmpty();
        remote.put(key, "Alice", Duration.ofMinutes(1));
        assertThat(tiered.get(key, String.class)).contains("Alice");
        assertThat(tiered.get(key, String.class)).contains("Alice");
        tiered.put(key, "Bob", Duration.ofMinutes(1));
        tiered.onInvalidation(CacheInvalidationEvent.of(key, CacheInvalidationOperation.PUT, "node-b"));

        assertThat(observationPublisher.events)
                .extracting(event -> event.tags().get("operation"))
                .contains("cache.get", "cache.put", "cache.invalidation_publish", "cache.invalidation_evict");
        assertThat(observationPublisher.events)
                .anySatisfy(event -> assertThat(event.tags()).containsEntry("tier", "l1").containsEntry("outcome", "miss"))
                .anySatisfy(event -> assertThat(event.tags()).containsEntry("tier", "l2").containsEntry("outcome", "miss"))
                .anySatisfy(event -> assertThat(event.tags()).containsEntry("tier", "l2").containsEntry("outcome", "hit"))
                .anySatisfy(event -> assertThat(event.tags()).containsEntry("tier", "l1").containsEntry("outcome", "hit"))
                .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "published"))
                .anySatisfy(event -> assertThat(event.tags()).containsEntry("outcome", "evicted"));
        assertThat(observationPublisher.events).allSatisfy(event -> {
            assertThat(event.tags()).containsEntry("capability", "cache");
            assertThat(event.tags()).containsEntry("provider", "tiered");
            assertThat(event.tags()).doesNotContainKeys("cache_key", "lock_token", "fencing_token", "exception");
        });
    }

    @Test
    void renewsOwnedLocalLock() {
        LocalCacheClient local = new LocalCacheClient(Duration.ofSeconds(1));
        LockHandle lock = local.tryLock(CacheKey.of("lock", "42"), Duration.ZERO, Duration.ofSeconds(1)).orElseThrow();

        assertThat(lock.renew(Duration.ofSeconds(2))).isTrue();

        lock.close();
        assertThat(local.tryLock(CacheKey.of("lock", "42"), Duration.ZERO, Duration.ofSeconds(1))).isPresent();
    }

    private static final class RecordingObservationPublisher implements NexaryObservationPublisher {
        private final List<NexaryObservationEvent> events = new ArrayList<>();

        @Override
        public void publish(NexaryObservationEvent event) {
            events.add(event);
        }
    }

    private static final class RecordingPublisher implements CacheInvalidationPublisher {
        private final ArrayList<CacheInvalidationEvent> events = new ArrayList<>();

        @Override
        public void publish(CacheInvalidationEvent event) {
            events.add(event);
        }
    }

    private static final class FailingCacheClient implements CacheClient {
        @Override
        public <T> Optional<T> get(CacheKey key, Class<T> type) {
            return Optional.empty();
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl) {
            throw new IllegalStateException("remote unavailable");
        }

        @Override
        public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
            throw new IllegalStateException("remote unavailable");
        }

        @Override
        public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
            return new LinkedHashMap<>();
        }

        @Override
        public void putAll(Map<CacheKey, ?> values, Duration ttl) {
            throw new IllegalStateException("remote unavailable");
        }

        @Override
        public boolean delete(CacheKey key) {
            throw new IllegalStateException("remote unavailable");
        }

        @Override
        public boolean expire(CacheKey key, Duration ttl) {
            throw new IllegalStateException("remote unavailable");
        }

        @Override
        public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
            return Optional.empty();
        }
    }
}
