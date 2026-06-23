package org.nexary.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.governance.runtime.GovernanceDecision;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernanceRejectedException;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.LocalGovernancePolicyRegistry;
import org.nexary.governance.runtime.LocalGovernanceRuntime;

class GovernedCacheClientTest {
    private static final CacheKey KEY = CacheKey.of("users", "42");

    @Test
    void allowsCacheOperationAndBindsCacheGovernanceContext() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingCacheClient delegate = new RecordingCacheClient();
        GovernanceRuntime runtime = runtime(GovernancePolicy.allowAll(), "cache.get", publisher);
        CacheClient cacheClient = new GovernedCacheClient(delegate, runtime, "redis");
        GovernanceContext upstream = GovernanceContext.builder()
                .trafficTag(TrafficTag.builder().priority(TrafficTag.Priority.HIGH).build())
                .attribute("caller", "sample")
                .build();

        Optional<String> value = GovernanceContext.callWithContext(upstream, () -> cacheClient.get(KEY, String.class));

        assertThat(value).contains("cached");
        assertThat(delegate.capturedContext).isNotNull();
        assertThat(delegate.capturedContext.resource()).isEqualTo(resource("cache.get"));
        assertThat(delegate.capturedContext.trafficTag().priority()).isEqualTo(TrafficTag.Priority.HIGH);
        assertThat(delegate.capturedContext.attributes()).containsEntry("caller", "sample");
        assertThat(publisher.operations()).isEmpty();
    }

    @Test
    void rejectsExpiredDeadlineBeforeDelegateRunsAndPublishesEvent() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingCacheClient delegate = new RecordingCacheClient();
        GovernanceRuntime runtime = runtime(GovernancePolicy.allowAll(), "cache.put", publisher);
        CacheClient cacheClient = new GovernedCacheClient(delegate, runtime, "redis");
        GovernanceContext upstream = GovernanceContext.builder()
                .deadline(Instant.now().minusMillis(10))
                .build();

        assertThatThrownBy(() -> GovernanceContext.callWithContext(upstream, () -> {
                    cacheClient.put(KEY, "cached", Duration.ofSeconds(30));
                    return null;
                }))
                .isInstanceOf(GovernanceRejectedException.class)
                .satisfies(error -> assertThat(((GovernanceRejectedException) error).decision().decision())
                        .isEqualTo(GovernanceDecision.Decision.DEADLINE_EXPIRED));

        assertThat(delegate.calls).isEmpty();
        assertThat(publisher.operations()).contains("governance.deadline.exceeded");
        assertThat(publisher.event("governance.deadline.exceeded")).satisfies(event -> assertThat(event.tags())
                .containsEntry("resource_kind", "cache")
                .containsEntry("resource", GovernedCacheClient.RESOURCE_NAME)
                .containsEntry("provider", "redis")
                .containsEntry("operation", "cache.put")
                .containsEntry("governance_action", "deadline_exceeded"));
    }

    @Test
    void rejectsWhenRateLimitIsExceededAndPublishesEvent() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingCacheClient delegate = new RecordingCacheClient();
        GovernanceRuntime runtime = runtime(
                GovernancePolicy.builder()
                        .maxRequestsPerWindow(1)
                        .rateLimitWindow(Duration.ofMinutes(1))
                        .build(),
                "cache.get",
                publisher);
        CacheClient cacheClient = new GovernedCacheClient(delegate, runtime, "redis");

        assertThat(cacheClient.get(KEY, String.class)).contains("cached");

        assertThatThrownBy(() -> cacheClient.get(KEY, String.class))
                .isInstanceOf(GovernanceRejectedException.class)
                .satisfies(error -> assertThat(((GovernanceRejectedException) error).decision().decision())
                        .isEqualTo(GovernanceDecision.Decision.RATE_LIMITED));

        assertThat(delegate.calls).containsExactly("get");
        assertThat(publisher.operations()).contains("governance.rate_limited");
    }

    @Test
    void rejectsDegradedOperationAndPublishesEvent() {
        RecordingPublisher publisher = new RecordingPublisher();
        RecordingCacheClient delegate = new RecordingCacheClient();
        GovernanceRuntime runtime = runtime(
                GovernancePolicy.builder().degraded(true).build(),
                "cache.delete",
                publisher);
        CacheClient cacheClient = new GovernedCacheClient(delegate, runtime, "redis");

        assertThatThrownBy(() -> cacheClient.delete(KEY))
                .isInstanceOf(GovernanceRejectedException.class)
                .satisfies(error -> assertThat(((GovernanceRejectedException) error).decision().decision())
                        .isEqualTo(GovernanceDecision.Decision.DEGRADED));

        assertThat(delegate.calls).isEmpty();
        assertThat(publisher.operations()).contains("governance.degraded");
        assertThat(publisher.event("governance.degraded")).satisfies(event -> assertThat(event.tags())
                .containsEntry("governance_action", "degraded")
                .containsEntry("outcome", "degraded"));
    }

    @Test
    void providerExceptionCanOpenCircuitThroughRuntimeExecute() {
        RecordingCacheClient delegate = new RecordingCacheClient();
        delegate.getFailure = new IllegalStateException("redis down");
        GovernanceRuntime runtime = runtime(
                GovernancePolicy.builder().consecutiveFailureThreshold(1).build(),
                "cache.get",
                new RecordingPublisher());
        CacheClient cacheClient = new GovernedCacheClient(delegate, runtime, "redis");

        assertThatThrownBy(() -> cacheClient.get(KEY, String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("redis down");

        delegate.getFailure = null;
        assertThatThrownBy(() -> cacheClient.get(KEY, String.class))
                .isInstanceOf(GovernanceRejectedException.class)
                .satisfies(error -> assertThat(((GovernanceRejectedException) error).decision().decision())
                        .isEqualTo(GovernanceDecision.Decision.CIRCUIT_OPEN));
        assertThat(delegate.calls).containsExactly("get");
    }

    @Test
    void slowProviderCallCanOpenCircuitThroughRuntimeExecute() {
        RecordingCacheClient delegate = new RecordingCacheClient();
        delegate.delay = Duration.ofMillis(20);
        GovernanceRuntime runtime = runtime(
                GovernancePolicy.builder()
                        .minimumRequests(1)
                        .slowCallDuration(Duration.ofMillis(1))
                        .slowCallThreshold(100.0d)
                        .build(),
                "cache.get",
                new RecordingPublisher());
        CacheClient cacheClient = new GovernedCacheClient(delegate, runtime, "redis");

        assertThat(cacheClient.get(KEY, String.class)).contains("cached");

        delegate.delay = Duration.ZERO;
        assertThatThrownBy(() -> cacheClient.get(KEY, String.class))
                .isInstanceOf(GovernanceRejectedException.class)
                .satisfies(error -> assertThat(((GovernanceRejectedException) error).decision().decision())
                        .isEqualTo(GovernanceDecision.Decision.CIRCUIT_OPEN));
        assertThat(delegate.calls).containsExactly("get");
    }

    private static GovernanceRuntime runtime(
            GovernancePolicy policy,
            String operation,
            RecordingPublisher publisher) {
        return new LocalGovernanceRuntime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource(operation), policy)
                        .build(),
                publisher);
    }

    private static GovernanceResource resource(String operation) {
        return GovernanceResource.cache(GovernedCacheClient.RESOURCE_NAME, "redis", operation);
    }

    private static final class RecordingPublisher implements NexaryObservationPublisher {
        private final List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(NexaryObservationEvent event) {
            events.add(event);
        }

        private NexaryObservationEvent event(String operation) {
            return events.stream()
                    .filter(event -> operation.equals(event.operation()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("missing event " + operation + " in " + operations()));
        }

        private List<String> operations() {
            return events.stream()
                    .map(NexaryObservationEvent::operation)
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    private static final class RecordingCacheClient implements CacheClient {
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private GovernanceContext capturedContext;
        private RuntimeException getFailure;
        private Duration delay = Duration.ZERO;

        @Override
        public <T> Optional<T> get(CacheKey key, Class<T> type) {
            calls.add("get");
            capturedContext = GovernanceContext.current().orElse(null);
            sleep(delay);
            if (getFailure != null) {
                throw getFailure;
            }
            return Optional.of(type.cast("cached"));
        }

        @Override
        public void put(CacheKey key, Object value, Duration ttl) {
            calls.add("put");
            capturedContext = GovernanceContext.current().orElse(null);
        }

        @Override
        public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
            calls.add("putIfAbsent");
            capturedContext = GovernanceContext.current().orElse(null);
            return true;
        }

        @Override
        public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
            calls.add("getAll");
            capturedContext = GovernanceContext.current().orElse(null);
            Map<CacheKey, Object> values = new LinkedHashMap<>();
            keys.forEach(key -> values.put(key, "cached"));
            return values;
        }

        @Override
        public void putAll(Map<CacheKey, ?> values, Duration ttl) {
            calls.add("putAll");
            capturedContext = GovernanceContext.current().orElse(null);
        }

        @Override
        public boolean delete(CacheKey key) {
            calls.add("delete");
            capturedContext = GovernanceContext.current().orElse(null);
            return true;
        }

        @Override
        public boolean expire(CacheKey key, Duration ttl) {
            calls.add("expire");
            capturedContext = GovernanceContext.current().orElse(null);
            return true;
        }

        @Override
        public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
            calls.add("tryLock");
            capturedContext = GovernanceContext.current().orElse(null);
            return Optional.empty();
        }

        @Override
        public <T> T cacheAside(CacheKey key, Class<T> type, Duration ttl, Supplier<T> loader) {
            calls.add("cacheAside");
            capturedContext = GovernanceContext.current().orElse(null);
            return loader.get();
        }
    }

    private static void sleep(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", ex);
        }
    }

}
