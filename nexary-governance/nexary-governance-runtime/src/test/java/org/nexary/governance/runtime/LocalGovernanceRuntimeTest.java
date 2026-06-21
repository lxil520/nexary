package org.nexary.governance.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.DeadlineContext;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.RequestPriority;
import org.nexary.core.observation.NexaryObservationEvent;

class LocalGovernanceRuntimeTest {
    @Test
    void rejectsWhenRateLimitIsExceededAndStopsRetry() throws Exception {
        GovernanceResource resource = GovernanceResource.http("profile-api", "get");
        RecordingPublisher publisher = new RecordingPublisher();
        GovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .maxRequestsPerWindow(1)
                                .rateLimitWindow(Duration.ofMinutes(1))
                                .build())
                        .build(),
                publisher);
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThat(runtime.execute(context, () -> "first")).isEqualTo("first");

        assertThatThrownBy(() -> runtime.execute(context, () -> "second"))
                .isInstanceOf(GovernanceRejectedException.class)
                .satisfies(error -> {
                    GovernanceRejectedException rejected = (GovernanceRejectedException) error;
                    assertThat(rejected.decision().decision()).isEqualTo(GovernanceDecision.Decision.RATE_LIMITED);
                    assertThat(rejected.decision().retrySignal()).isNotNull();
                });
        assertThat(publisher.operations()).contains("governance.rate_limited", "governance.retry.stopped");
    }

    @Test
    void returnsFallbackWhenDegraded() throws Exception {
        GovernanceResource resource = GovernanceResource.downstream("billing-service", "charge");
        RecordingPublisher publisher = new RecordingPublisher();
        GovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder().degraded(true).build())
                        .build(),
                publisher);
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        String result = runtime.execute(context, () -> "main", () -> "fallback");

        assertThat(result).isEqualTo("fallback");
        assertThat(publisher.operations()).contains("governance.degraded", "governance.retry.stopped");
    }

    @Test
    void rejectsExpiredDeadlineBeforeActionRuns() {
        GovernanceResource resource = GovernanceResource.http("checkout-api", "submit");
        RecordingPublisher publisher = new RecordingPublisher();
        GovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder().policy(resource, GovernancePolicy.allowAll()).build(),
                publisher);
        GovernanceContext context = GovernanceContext.builder()
                .resource(resource)
                .deadline(Instant.now().minusMillis(10))
                .build();

        assertThatThrownBy(() -> runtime.execute(context, () -> "never"))
                .isInstanceOf(GovernanceRejectedException.class)
                .satisfies(error -> assertThat(((GovernanceRejectedException) error).decision().decision())
                        .isEqualTo(GovernanceDecision.Decision.DEADLINE_EXPIRED));
        assertThat(publisher.operations()).contains("governance.deadline.exceeded", "governance.retry.stopped");
    }

    @Test
    void enforcesConcurrencyLimit() throws Exception {
        GovernanceResource resource = GovernanceResource.http("inventory-api", "reserve");
        RecordingPublisher publisher = new RecordingPublisher();
        GovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder().maxConcurrency(1).build())
                        .build(),
                publisher);
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FutureTask<String> first = new FutureTask<>(() -> runtime.execute(context, () -> {
            entered.countDown();
            release.await();
            return "first";
        }));
        Thread worker = new Thread(first);
        worker.start();
        entered.await();

        assertThat(runtime.execute(context, () -> "second", () -> "fallback")).isEqualTo("fallback");
        release.countDown();
        assertThat(first.get()).isEqualTo("first");
        assertThat(publisher.operations()).contains("governance.bulkhead.rejected", "governance.retry.stopped");
    }

    @Test
    void selectsPolicyByPriority() throws Exception {
        GovernanceResource resource = GovernanceResource.http("search-api", "query");
        GovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, RequestPriority.LOW, GovernancePolicy.builder().degraded(true).build())
                        .policy(resource, RequestPriority.HIGH, GovernancePolicy.allowAll())
                        .build(),
                new RecordingPublisher());

        GovernanceContext low = GovernanceContext.builder()
                .resource(resource)
                .trafficTag(TrafficTag.builder().priority(TrafficTag.Priority.LOW).build())
                .build();
        GovernanceContext high = GovernanceContext.builder()
                .resource(resource)
                .trafficTag(TrafficTag.builder().priority(TrafficTag.Priority.HIGH).build())
                .build();

        assertThat(runtime.execute(low, () -> "main", () -> "low-fallback")).isEqualTo("low-fallback");
        assertThat(runtime.execute(high, () -> "high")).isEqualTo("high");
    }

    @Test
    void bindsGovernanceAndDeadlineContextDuringAction() throws Exception {
        GovernanceResource resource = GovernanceResource.http("profile-api", "get");
        GovernanceContext context = GovernanceContext.builder()
                .resource(resource)
                .deadline(Instant.now().plusSeconds(5))
                .build();
        GovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder().policy(resource, GovernancePolicy.allowAll()).build(),
                new RecordingPublisher());

        String result = runtime.execute(context, () -> {
            assertThat(GovernanceContext.current()).contains(context);
            assertThat(DeadlineContext.current()).isPresent();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(GovernanceContext.current()).isEmpty();
        assertThat(DeadlineContext.current()).isEmpty();
    }

    @Test
    void appliesPolicyDeadlineDuringAction() throws Exception {
        GovernanceResource resource = GovernanceResource.http("payments-api", "authorize");
        GovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder().deadline(Duration.ofSeconds(5)).build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        String result = runtime.execute(context, () -> {
            assertThat(GovernanceContext.current()).isPresent();
            assertThat(GovernanceContext.current().get().deadline()).isPresent();
            assertThat(DeadlineContext.current()).isPresent();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(GovernanceContext.current()).isEmpty();
        assertThat(DeadlineContext.current()).isEmpty();
    }

    private static GovernanceRuntime runtime(GovernancePolicyRegistry registry, RecordingPublisher publisher) {
        return new LocalGovernanceRuntime(registry, publisher);
    }

    private static final class RecordingPublisher implements org.nexary.core.observation.NexaryObservationPublisher {
        private final List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(NexaryObservationEvent event) {
            events.add(event);
        }

        private List<String> operations() {
            return events.stream()
                    .map(NexaryObservationEvent::operation)
                    .collect(java.util.stream.Collectors.toList());
        }
    }
}
