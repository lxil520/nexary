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
        LocalGovernanceRuntime runtime = runtime(
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

        GovernanceRuntimeSnapshot running = runtime.snapshots().get(0);
        assertThat(running.activeConcurrency()).isEqualTo(1);
        assertThat(running.maxConcurrency()).isEqualTo(1);
        assertThat(runtime.execute(context, () -> "second", () -> "fallback")).isEqualTo("fallback");
        GovernanceRuntimeSnapshot rejected = runtime.snapshots().get(0);
        assertThat(rejected.lastOutcome()).isEqualTo(GovernanceCallOutcome.REJECTED);
        assertThat(rejected.lastOutcomeAt()).isPresent();
        assertThat(rejected.lastRejectionReason()).isEqualTo(GovernanceRejectionReason.CONCURRENCY_LIMITED);
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

    @Test
    void opensCircuitFromActionFailuresAndRejectsWithFallback() throws Exception {
        GovernanceResource resource = GovernanceResource.http("profile-api", "get");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .minimumRequests(2)
                                .failureRateThreshold(50.0)
                                .slidingWindowSize(10)
                                .slidingWindowDuration(Duration.ofMinutes(1))
                                .openStateDuration(Duration.ofSeconds(5))
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("first");
        })).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("second");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(runtime.execute(context, () -> "main", () -> "fallback")).isEqualTo("fallback");
        GovernanceRuntimeSnapshot snapshot = runtime.snapshots().get(0);
        assertThat(snapshot.circuitState()).isEqualTo(GovernanceCircuitState.OPEN);
        assertThat(snapshot.windowCalls()).isEqualTo(2);
        assertThat(snapshot.windowFailures()).isEqualTo(2);
        assertThat(snapshot.lastRejectionReason()).isEqualTo(GovernanceRejectionReason.CIRCUIT_OPEN);
        assertThat(snapshot.lastOutcome()).isEqualTo(GovernanceCallOutcome.REJECTED);
        assertThat(snapshot.lastOutcomeAt()).isPresent();
        assertThat(snapshot.lastStateTransitionAt()).isPresent();
    }

    @Test
    void opensCircuitFromSlowCalls() throws Exception {
        GovernanceResource resource = GovernanceResource.http("search-api", "query");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .minimumRequests(1)
                                .slowCallThreshold(100.0)
                                .slowCallDuration(Duration.ofMillis(5))
                                .openStateDuration(Duration.ofSeconds(5))
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThat(runtime.execute(context, () -> {
            Thread.sleep(20);
            return "slow";
        })).isEqualTo("slow");

        assertThat(runtime.execute(context, () -> "main", () -> "fallback")).isEqualTo("fallback");
        GovernanceRuntimeSnapshot snapshot = runtime.snapshots().get(0);
        assertThat(snapshot.circuitState()).isEqualTo(GovernanceCircuitState.OPEN);
        assertThat(snapshot.windowSlowCalls()).isEqualTo(1);
    }

    @Test
    void halfOpenSuccessRestoresClosedState() throws Exception {
        GovernanceResource resource = GovernanceResource.http("inventory-api", "reserve");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .consecutiveFailureThreshold(1)
                                .openStateDuration(Duration.ofMillis(20))
                                .halfOpenMaxCalls(1)
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("down");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(runtime.execute(context, () -> "main", () -> "fallback")).isEqualTo("fallback");
        GovernanceRuntimeSnapshot open = runtime.snapshots().get(0);
        assertThat(open.circuitState()).isEqualTo(GovernanceCircuitState.OPEN);
        assertThat(open.openUntil()).isPresent();
        assertThat(open.lastOutcome()).isEqualTo(GovernanceCallOutcome.REJECTED);
        assertThat(open.lastRejectionReason()).isEqualTo(GovernanceRejectionReason.CIRCUIT_OPEN);
        Instant openedAt = open.lastStateTransitionAt().orElseThrow(AssertionError::new);

        Thread.sleep(40);

        GovernanceRuntimeSnapshot halfOpen = runtime.snapshots().get(0);
        assertThat(halfOpen.circuitState()).isEqualTo(GovernanceCircuitState.HALF_OPEN);
        assertThat(halfOpen.lastStateTransitionAt())
                .hasValueSatisfying(transition -> assertThat(transition).isAfterOrEqualTo(openedAt));
        Instant halfOpenedAt = halfOpen.lastStateTransitionAt().orElseThrow(AssertionError::new);

        assertThat(runtime.execute(context, () -> "recovered")).isEqualTo("recovered");
        GovernanceRuntimeSnapshot closed = runtime.snapshots().get(0);
        assertThat(closed.circuitState()).isEqualTo(GovernanceCircuitState.CLOSED);
        assertThat(closed.openUntil()).isEmpty();
        assertThat(closed.lastOutcome()).isEqualTo(GovernanceCallOutcome.SUCCESS);
        assertThat(closed.lastStateTransitionAt())
                .hasValueSatisfying(transition -> assertThat(transition).isAfterOrEqualTo(halfOpenedAt));
        assertThat(runtime.execute(context, () -> "after")).isEqualTo("after");
    }

    @Test
    void halfOpenRequiresConfiguredSuccessfulProbesBeforeClosing() throws Exception {
        GovernanceResource resource = GovernanceResource.http("inventory-api", "reserve");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .consecutiveFailureThreshold(1)
                                .openStateDuration(Duration.ofMillis(20))
                                .halfOpenMaxCalls(2)
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("down");
        })).isInstanceOf(IllegalStateException.class);
        Thread.sleep(40);

        assertThat(runtime.execute(context, () -> "probe-1")).isEqualTo("probe-1");
        assertThat(runtime.snapshots().get(0).circuitState()).isEqualTo(GovernanceCircuitState.HALF_OPEN);
        assertThat(runtime.execute(context, () -> "probe-2")).isEqualTo("probe-2");
        assertThat(runtime.snapshots().get(0).circuitState()).isEqualTo(GovernanceCircuitState.CLOSED);
    }

    @Test
    void halfOpenFailureReopensCircuit() throws Exception {
        GovernanceResource resource = GovernanceResource.http("billing-api", "charge");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .consecutiveFailureThreshold(1)
                                .openStateDuration(Duration.ofMillis(20))
                                .halfOpenMaxCalls(1)
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("down");
        })).isInstanceOf(IllegalStateException.class);
        Thread.sleep(40);

        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("still down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(runtime.execute(context, () -> "main", () -> "fallback")).isEqualTo("fallback");
        assertThat(runtime.snapshots().get(0).circuitState()).isEqualTo(GovernanceCircuitState.OPEN);
    }

    @Test
    void snapshotsExposeLowCardinalityRuntimeDiagnostics() throws Exception {
        GovernanceResource resource = GovernanceResource.http("catalog-api", "list");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .maxRequestsPerWindow(5)
                                .rateLimitWindow(Duration.ofSeconds(10))
                                .maxConcurrency(3)
                                .minimumRequests(2)
                                .failureRateThreshold(75.0)
                                .slowCallThreshold(80.0)
                                .slowCallDuration(Duration.ofMillis(250))
                                .openStateDuration(Duration.ofSeconds(3))
                                .halfOpenMaxCalls(2)
                                .slidingWindowSize(4)
                                .slidingWindowDuration(Duration.ofSeconds(20))
                                .consecutiveFailureThreshold(3)
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder()
                .resource(resource)
                .trafficTag(TrafficTag.builder()
                        .tenant("tenant-1")
                        .bizKey("user-123")
                        .priority(TrafficTag.Priority.HIGH)
                        .build())
                .attribute("request_id", "abc")
                .build();

        assertThat(runtime.execute(context, () -> "ok")).isEqualTo("ok");

        GovernanceRuntimeSnapshot snapshot = runtime.snapshots().get(0);
        assertThat(snapshot.resourceKey()).isEqualTo(resource.key());
        assertThat(snapshot.priority()).isEqualTo("high");
        assertThat(snapshot.windowCalls()).isEqualTo(1);
        assertThat(snapshot.lastRejectionReason()).isEqualTo(GovernanceRejectionReason.NONE);
        assertThat(snapshot.lastOutcome()).isEqualTo(GovernanceCallOutcome.SUCCESS);
        assertThat(snapshot.lastOutcomeAt()).isPresent();
        assertThat(snapshot.activeConcurrency()).isZero();
        assertThat(snapshot.maxConcurrency()).isEqualTo(3);
        assertThat(snapshot.maxRequestsPerWindow()).isEqualTo(5);
        assertThat(snapshot.rateLimitWindow()).isEqualTo(Duration.ofSeconds(10));
        assertThat(snapshot.minimumRequests()).isEqualTo(2);
        assertThat(snapshot.failureRateThreshold()).isEqualTo(75.0);
        assertThat(snapshot.slowCallThreshold()).isEqualTo(80.0);
        assertThat(snapshot.slowCallDuration()).hasValue(Duration.ofMillis(250));
        assertThat(snapshot.openStateDuration()).isEqualTo(Duration.ofSeconds(3));
        assertThat(snapshot.halfOpenMaxCalls()).isEqualTo(2);
        assertThat(snapshot.slidingWindowSize()).isEqualTo(4);
        assertThat(snapshot.slidingWindowDuration()).isEqualTo(Duration.ofSeconds(20));
        assertThat(snapshot.consecutiveFailureThreshold()).isEqualTo(3);
        assertThat(snapshot.lastStateTransitionAt()).isPresent();
        assertThat(snapshot.resourceKey()).doesNotContain("tenant-1", "user-123", "abc");
        assertThat(snapshot.priority()).doesNotContain("tenant-1", "user-123", "abc");
        assertThat(snapshot.lastOutcome().name()).doesNotContain("tenant-1", "user-123", "abc");
        assertThat(snapshot.toString()).doesNotContain("tenant-1", "user-123", "abc");
    }

    @Test
    void snapshotsTrackSuccessFailureAndCircuitTransitions() throws Exception {
        GovernanceResource resource = GovernanceResource.http("orders-api", "submit");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .maxConcurrency(2)
                                .consecutiveFailureThreshold(2)
                                .openStateDuration(Duration.ofSeconds(5))
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThat(runtime.execute(context, () -> "ok")).isEqualTo("ok");
        GovernanceRuntimeSnapshot success = runtime.snapshots().get(0);
        assertThat(success.lastOutcome()).isEqualTo(GovernanceCallOutcome.SUCCESS);
        assertThat(success.lastOutcomeAt()).isPresent();
        Instant initialTransition = success.lastStateTransitionAt().orElseThrow(AssertionError::new);

        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("first");
        })).isInstanceOf(IllegalStateException.class);
        GovernanceRuntimeSnapshot firstFailure = runtime.snapshots().get(0);
        assertThat(firstFailure.lastOutcome()).isEqualTo(GovernanceCallOutcome.FAILURE);
        assertThat(firstFailure.circuitState()).isEqualTo(GovernanceCircuitState.CLOSED);
        assertThat(firstFailure.lastStateTransitionAt()).hasValue(initialTransition);

        assertThatThrownBy(() -> runtime.execute(context, () -> {
            throw new IllegalStateException("second");
        })).isInstanceOf(IllegalStateException.class);
        GovernanceRuntimeSnapshot open = runtime.snapshots().get(0);
        assertThat(open.lastOutcome()).isEqualTo(GovernanceCallOutcome.FAILURE);
        assertThat(open.circuitState()).isEqualTo(GovernanceCircuitState.OPEN);
        assertThat(open.openUntil()).isPresent();
        assertThat(open.lastStateTransitionAt())
                .hasValueSatisfying(transition -> assertThat(transition).isAfterOrEqualTo(initialTransition));

        assertThat(runtime.execute(context, () -> "main", () -> "fallback")).isEqualTo("fallback");
        GovernanceRuntimeSnapshot rejected = runtime.snapshots().get(0);
        assertThat(rejected.lastOutcome()).isEqualTo(GovernanceCallOutcome.REJECTED);
        assertThat(rejected.lastRejectionReason()).isEqualTo(GovernanceRejectionReason.CIRCUIT_OPEN);
        assertThat(rejected.totalRejections()).isEqualTo(1);
    }

    @Test
    void resourcesEventsAndSummaryExposeOnlyLowCardinalityFields() throws Exception {
        GovernanceResource resource = GovernanceResource.http("catalog-api", "list");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .maxRequestsPerWindow(1)
                                .rateLimitWindow(Duration.ofMinutes(1))
                                .maxConcurrency(4)
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext context = GovernanceContext.builder()
                .resource(resource)
                .trafficTag(TrafficTag.builder()
                        .tenant("tenant-42")
                        .bizKey("user-42")
                        .priority(TrafficTag.Priority.HIGH)
                        .build())
                .attribute("message_id", "m-1")
                .attribute("cache_key", "cache-user-42")
                .attribute("payload", "payload-secret")
                .build();

        assertThat(runtime.execute(context, () -> "ok")).isEqualTo("ok");
        assertThat(runtime.execute(context, () -> "fallback", () -> "fallback")).isEqualTo("fallback");

        GovernanceResourceDescriptor descriptor = runtime.resources().get(0);
        assertThat(descriptor.resourceKey()).isEqualTo(resource.key());
        assertThat(descriptor.kind()).isEqualTo(resource.kind());
        assertThat(descriptor.name()).isEqualTo("catalog-api");
        assertThat(descriptor.provider()).isEqualTo("nexary");
        assertThat(descriptor.operation()).isEqualTo("list");
        assertThat(descriptor.priority()).isEqualTo("high");
        assertThat(descriptor.policySnapshot().maxRequestsPerWindow()).isEqualTo(1);
        assertThat(descriptor.runtimeSnapshot().lastRejectionReason()).isEqualTo(GovernanceRejectionReason.RATE_LIMITED);

        assertThat(runtime.recentEvents())
                .hasSize(2)
                .extracting(GovernanceRuntimeEvent::outcome)
                .containsExactly(GovernanceCallOutcome.SUCCESS, GovernanceCallOutcome.REJECTED);
        GovernanceRuntimeEvent rejected = runtime.recentEvents().get(1);
        assertThat(rejected.action()).isEqualTo(GovernanceRuntimeAction.FALLBACK);
        assertThat(rejected.rejectionReason()).isEqualTo(GovernanceRejectionReason.RATE_LIMITED);
        assertThat(rejected.durationBucket()).isNotNull();

        GovernanceRuntimeSummary summary = runtime.summary();
        assertThat(summary.resourceCount()).isEqualTo(2);
        assertThat(summary.snapshotCount()).isEqualTo(1);
        assertThat(summary.eventCount()).isEqualTo(2);
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.rejectedCount()).isEqualTo(1);
        assertThat(summary.fallbackCount()).isEqualTo(1);
        assertThat(summary.lastEventAt()).isPresent();

        assertThat(descriptor.toString())
                .doesNotContain("tenant-42", "user-42", "m-1", "cache-user-42", "payload-secret");
        assertThat(runtime.recentEvents().toString())
                .doesNotContain("tenant-42", "user-42", "m-1", "cache-user-42", "payload-secret");
        assertThat(summary.toString())
                .doesNotContain("tenant-42", "user-42", "m-1", "cache-user-42", "payload-secret");
    }

    @Test
    void recentEventsUseFixedSizeOldestToNewestRingBuffer() throws Exception {
        GovernanceResource firstResource = GovernanceResource.http("audit-api", "first");
        GovernanceResource secondResource = GovernanceResource.http("audit-api", "second");
        GovernanceResource thirdResource = GovernanceResource.http("audit-api", "third");
        LocalGovernanceRuntime runtime = new LocalGovernanceRuntime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(firstResource, GovernancePolicy.allowAll())
                        .policy(secondResource, GovernancePolicy.allowAll())
                        .policy(thirdResource, GovernancePolicy.allowAll())
                        .build(),
                new RecordingPublisher(),
                2);

        runtime.execute(GovernanceContext.builder().resource(firstResource).build(), () -> "one");
        runtime.execute(GovernanceContext.builder().resource(secondResource).build(), () -> "two");
        runtime.execute(GovernanceContext.builder().resource(thirdResource).build(), () -> "three");

        assertThat(runtime.recentEvents()).hasSize(2);
        assertThat(runtime.recentEvents())
                .extracting(GovernanceRuntimeEvent::resourceKey)
                .containsExactly(secondResource.key(), thirdResource.key());
        assertThat(runtime.summary().eventCount()).isEqualTo(2);
    }

    @Test
    void summaryAggregatesSuccessFailureRejectedFallbackAndCircuitState() throws Exception {
        GovernanceResource degradedResource = GovernanceResource.http("summary-api", "degraded");
        GovernanceResource circuitResource = GovernanceResource.http("summary-api", "circuit");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(degradedResource, GovernancePolicy.builder().degraded(true).build())
                        .policy(circuitResource, GovernancePolicy.builder()
                                .consecutiveFailureThreshold(1)
                                .openStateDuration(Duration.ofSeconds(5))
                                .build())
                        .build(),
                new RecordingPublisher());
        GovernanceContext degraded = GovernanceContext.builder().resource(degradedResource).build();
        GovernanceContext circuit = GovernanceContext.builder().resource(circuitResource).build();

        assertThat(runtime.execute(circuit, () -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> runtime.execute(circuit, () -> {
            throw new IllegalStateException("downstream userId=42 payload=secret");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(runtime.execute(circuit, () -> "main", () -> "fallback")).isEqualTo("fallback");
        assertThat(runtime.execute(degraded, () -> "main", () -> "degraded-fallback")).isEqualTo("degraded-fallback");

        GovernanceRuntimeSummary summary = runtime.summary();
        assertThat(summary.resourceCount()).isEqualTo(2);
        assertThat(summary.snapshotCount()).isEqualTo(2);
        assertThat(summary.eventCount()).isEqualTo(4);
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.failureCount()).isEqualTo(1);
        assertThat(summary.rejectedCount()).isEqualTo(2);
        assertThat(summary.fallbackCount()).isEqualTo(2);
        assertThat(summary.openCircuitCount()).isEqualTo(1);
        assertThat(summary.degradedResourceCount()).isEqualTo(1);
        assertThat(runtime.recentEvents().toString()).doesNotContain("userId=42", "payload=secret");
    }

    @Test
    void resourcePolicySnapshotMatchesRuntimeSnapshotPolicyFields() throws Exception {
        GovernanceResource resource = GovernanceResource.http("policy-api", "read");
        LocalGovernanceRuntime runtime = runtime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .maxRequestsPerWindow(7)
                                .rateLimitWindow(Duration.ofSeconds(8))
                                .maxConcurrency(9)
                                .minimumRequests(10)
                                .failureRateThreshold(11.0)
                                .slowCallThreshold(12.0)
                                .slowCallDuration(Duration.ofMillis(13))
                                .openStateDuration(Duration.ofSeconds(14))
                                .halfOpenMaxCalls(15)
                                .slidingWindowSize(16)
                                .slidingWindowDuration(Duration.ofSeconds(17))
                                .consecutiveFailureThreshold(18)
                                .build())
                        .build(),
                new RecordingPublisher());

        assertThat(runtime.execute(GovernanceContext.builder().resource(resource).build(), () -> "ok")).isEqualTo("ok");

        GovernanceRuntimeSnapshot runtimeSnapshot = runtime.snapshots().get(0);
        GovernancePolicySnapshot policySnapshot = runtime.resources().get(0).policySnapshot();
        assertThat(policySnapshot.maxRequestsPerWindow()).isEqualTo(runtimeSnapshot.maxRequestsPerWindow());
        assertThat(policySnapshot.rateLimitWindow()).isEqualTo(runtimeSnapshot.rateLimitWindow());
        assertThat(policySnapshot.maxConcurrency()).isEqualTo(runtimeSnapshot.maxConcurrency());
        assertThat(policySnapshot.degraded()).isEqualTo(runtimeSnapshot.degraded());
        assertThat(policySnapshot.minimumRequests()).isEqualTo(runtimeSnapshot.minimumRequests());
        assertThat(policySnapshot.failureRateThreshold()).isEqualTo(runtimeSnapshot.failureRateThreshold());
        assertThat(policySnapshot.slowCallThreshold()).isEqualTo(runtimeSnapshot.slowCallThreshold());
        assertThat(policySnapshot.slowCallDuration()).isEqualTo(runtimeSnapshot.slowCallDuration());
        assertThat(policySnapshot.openStateDuration()).isEqualTo(runtimeSnapshot.openStateDuration());
        assertThat(policySnapshot.halfOpenMaxCalls()).isEqualTo(runtimeSnapshot.halfOpenMaxCalls());
        assertThat(policySnapshot.slidingWindowSize()).isEqualTo(runtimeSnapshot.slidingWindowSize());
        assertThat(policySnapshot.slidingWindowDuration()).isEqualTo(runtimeSnapshot.slidingWindowDuration());
        assertThat(policySnapshot.consecutiveFailureThreshold()).isEqualTo(runtimeSnapshot.consecutiveFailureThreshold());
    }

    private static LocalGovernanceRuntime runtime(GovernancePolicyRegistry registry, RecordingPublisher publisher) {
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
