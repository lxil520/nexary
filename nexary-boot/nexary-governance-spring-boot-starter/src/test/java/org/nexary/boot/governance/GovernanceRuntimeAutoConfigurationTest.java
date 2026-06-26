package org.nexary.boot.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.CancellationContext;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.context.CancellationToken;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.GovernanceInstanceHealth;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernancePolicyRegistry;
import org.nexary.governance.runtime.InstanceHealthSignal;
import org.nexary.governance.runtime.InstanceHealthSignalType;
import org.nexary.governance.runtime.InstanceStatusCodeClass;
import org.nexary.governance.runtime.GovernanceCallOutcome;
import org.nexary.governance.runtime.GovernanceDurationBucket;
import org.nexary.governance.runtime.GovernanceInstanceRef;
import org.nexary.governance.runtime.GovernanceTraceRecorder;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class GovernanceRuntimeAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GovernanceRuntimeAutoConfiguration.class));
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GovernanceRuntimeAutoConfiguration.class,
                    GovernanceDiagnosticsAutoConfiguration.class,
                    GovernanceCancellationAutoConfiguration.class));

    @Test
    void createsLocalRuntimeByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(GovernanceRuntime.class));
    }

    @Test
    void createsRuntimeBackedGovernanceExecutionByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(GovernanceExecution.class));
    }

    @Test
    void canDisableRuntime() {
        contextRunner
                .withPropertyValues("nexary.governance.runtime.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GovernanceRuntime.class));
    }

    @Test
    void bindsTypedPolicyConfigurationIntoRegistry() {
        contextRunner
                .withPropertyValues(
                        "nexary.governance.default-policy.deadline=2s",
                        "nexary.governance.default-policy.max-concurrency=8",
                        "nexary.governance.resources.checkout.kind=http",
                        "nexary.governance.resources.checkout.name=checkout-api",
                        "nexary.governance.resources.checkout.operation=submit",
                        "nexary.governance.resources.checkout.max-requests-per-window=2",
                        "nexary.governance.resources.checkout.rate-limit-window=30s",
                        "nexary.governance.resources.checkout.max-concurrency=1",
                        "nexary.governance.resources.checkout.priorities.low.degraded=true")
                .run(context -> {
                    GovernancePolicyRegistry registry = context.getBean(GovernancePolicyRegistry.class);
                    GovernanceResource resource = GovernanceResource.http("checkout-api", "submit");

                    GovernancePolicy normalPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(resource)
                            .build());
                    GovernancePolicy lowPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(resource)
                            .trafficTag(TrafficTag.builder().priority(TrafficTag.Priority.LOW).build())
                            .build());
                    GovernancePolicy defaultPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(GovernanceResource.http("other-api", "submit"))
                            .build());

                    assertThat(normalPolicy.maxRequestsPerWindow()).isEqualTo(2);
                    assertThat(normalPolicy.rateLimitWindow()).hasSeconds(30);
                    assertThat(normalPolicy.maxConcurrency()).isEqualTo(1);
                    assertThat(lowPolicy.degraded()).isTrue();
                    assertThat(defaultPolicy.deadline()).hasValueSatisfying(deadline ->
                            assertThat(deadline).hasSeconds(2));
                    assertThat(defaultPolicy.maxConcurrency()).isEqualTo(8);
                });
    }

    @Test
    void keepsRuntimeScopedDefaultPolicyConfiguration() {
        contextRunner
                .withPropertyValues("nexary.governance.runtime.default-policy.max-concurrency=3")
                .run(context -> {
                    GovernancePolicyRegistry registry = context.getBean(GovernancePolicyRegistry.class);
                    GovernancePolicy defaultPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(GovernanceResource.http("legacy-api", "get"))
                            .build());

                    assertThat(defaultPolicy.maxConcurrency()).isEqualTo(3);
                });
    }

    @Test
    void bindsCircuitBreakerPolicyConfiguration() {
        contextRunner
                .withPropertyValues(
                        "nexary.governance.resources.cache.kind=cache",
                        "nexary.governance.resources.cache.name=cache-client",
                        "nexary.governance.resources.cache.provider=redis",
                        "nexary.governance.resources.cache.operation=cache.get",
                        "nexary.governance.resources.cache.circuit-breaker.enabled=true",
                        "nexary.governance.resources.cache.circuit-breaker.window=20s",
                        "nexary.governance.resources.cache.circuit-breaker.minimum-calls=4",
                        "nexary.governance.resources.cache.circuit-breaker.failure-rate-threshold=25.5",
                        "nexary.governance.resources.cache.circuit-breaker.slow-call-threshold=150ms",
                        "nexary.governance.resources.cache.circuit-breaker.slow-call-rate-threshold=75.0",
                        "nexary.governance.resources.cache.circuit-breaker.half-open-probe-calls=2",
                        "nexary.governance.resources.cache.circuit-breaker.open-state-duration=5s",
                        "nexary.governance.resources.cache.circuit-breaker.sliding-window-size=8",
                        "nexary.governance.resources.cache.circuit-breaker.consecutive-failure-threshold=3")
                .run(context -> {
                    GovernanceRuntimeProperties properties = context.getBean(GovernanceRuntimeProperties.class);
                    GovernanceRuntimeProperties.CircuitBreaker circuitBreaker =
                            properties.getResources().get("cache").getCircuitBreaker();
                    GovernancePolicyRegistry registry = context.getBean(GovernancePolicyRegistry.class);
                    GovernancePolicy policy = registry.policyFor(GovernanceContext.builder()
                            .resource(GovernanceResource.cache("cache-client", "redis", "cache.get"))
                            .build());

                    assertThat(circuitBreaker.isEnabled()).isTrue();
                    assertThat(circuitBreaker.getWindow()).hasSeconds(20);
                    assertThat(circuitBreaker.getMinimumCalls()).isEqualTo(4);
                    assertThat(circuitBreaker.getFailureRateThreshold()).isEqualTo(25.5d);
                    assertThat(circuitBreaker.getSlowCallThreshold()).hasMillis(150);
                    assertThat(circuitBreaker.getSlowCallRateThreshold()).isEqualTo(75.0d);
                    assertThat(circuitBreaker.getHalfOpenProbeCalls()).isEqualTo(2);
                    assertThat(circuitBreaker.getOpenStateDuration()).hasSeconds(5);
                    assertThat(circuitBreaker.getSlidingWindowSize()).isEqualTo(8);
                    assertThat(circuitBreaker.getConsecutiveFailureThreshold()).isEqualTo(3);

                    assertThat(policy.circuitBreakerEnabled()).isTrue();
                    assertThat(policy.minimumRequests()).isEqualTo(4);
                    assertThat(policy.failureRateThreshold()).isEqualTo(25.5d);
                    assertThat(policy.slowCallDuration()).hasValueSatisfying(duration -> assertThat(duration).hasMillis(150));
                    assertThat(policy.slowCallThreshold()).isEqualTo(75.0d);
                    assertThat(policy.halfOpenMaxCalls()).isEqualTo(2);
                    assertThat(policy.openStateDuration()).hasSeconds(5);
                    assertThat(policy.slidingWindowSize()).isEqualTo(8);
                    assertThat(policy.slidingWindowDuration()).hasSeconds(20);
                    assertThat(policy.consecutiveFailureThreshold()).isEqualTo(3);
	                });
    }

    @Test
    void diagnosticsEndpointIsDisabledByDefault() {
        webContextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(GovernanceDiagnosticsAutoConfiguration.GovernanceDiagnosticsEndpoint.class));
    }

    @Test
    void diagnosticsEndpointIsEnabledExplicitly() {
        webContextRunner
                .withPropertyValues("nexary.governance.diagnostics.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(GovernanceDiagnosticsAutoConfiguration.GovernanceDiagnosticsEndpoint.class));
    }

    @Test
    void instanceHealthRecorderIsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(GovernanceInstanceHealth.class));
    }

    @Test
    void instanceHealthRecorderBindsTypedConfigurationWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "nexary.governance.instance-health.enabled=true",
                        "nexary.governance.instance-health.window=30s",
                        "nexary.governance.instance-health.minimum-calls=4",
                        "nexary.governance.instance-health.suspect-windows=1",
                        "nexary.governance.instance-health.recovery-windows=1",
                        "nexary.governance.instance-health.slow-call-threshold=750ms",
                        "nexary.governance.instance-health.slow-ratio-threshold=0.5",
                        "nexary.governance.instance-health.failure-ratio-threshold=0.4",
                        "nexary.governance.instance-health.timeout-ratio-threshold=0.3",
                        "nexary.governance.instance-health.skew-factor-threshold=2.5")
                .run(context -> {
                    assertThat(context).hasSingleBean(GovernanceInstanceHealth.class);
                    GovernanceRuntimeProperties.InstanceHealth properties =
                            context.getBean(GovernanceRuntimeProperties.class).getInstanceHealth();
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getWindow()).hasSeconds(30);
                    assertThat(properties.getMinimumCalls()).isEqualTo(4);
                    assertThat(properties.getSuspectWindows()).isEqualTo(1);
                    assertThat(properties.getRecoveryWindows()).isEqualTo(1);
                    assertThat(properties.getSlowCallThreshold()).hasMillis(750);
                    assertThat(properties.getSlowRatioThreshold()).isEqualTo(0.5d);
                    assertThat(properties.getFailureRatioThreshold()).isEqualTo(0.4d);
                    assertThat(properties.getTimeoutRatioThreshold()).isEqualTo(0.3d);
                    assertThat(properties.getSkewFactorThreshold()).isEqualTo(2.5d);
                });
    }

    @Test
    void instanceHealthEndpointRequiresDiagnosticsAndFeatureSwitches() {
        webContextRunner
                .withPropertyValues("nexary.governance.instance-health.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(GovernanceDiagnosticsAutoConfiguration.GovernanceInstanceHealthEndpoint.class));

        webContextRunner
                .withPropertyValues(
                        "nexary.governance.diagnostics.enabled=true",
                        "nexary.governance.instance-health.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(GovernanceDiagnosticsAutoConfiguration.GovernanceInstanceHealthEndpoint.class));
    }

    @Test
    void instanceHealthEndpointReturnsStableJsonShape() {
        webContextRunner
                .withPropertyValues(
                        "nexary.governance.diagnostics.enabled=true",
                        "nexary.governance.instance-health.enabled=true")
                .run(context -> {
                    GovernanceInstanceHealth health = context.getBean(GovernanceInstanceHealth.class);
                    GovernanceInstanceRef ref = GovernanceInstanceRef.of(
                            "downstream:profile-service:lookup",
                            "profile-service",
                            "10.24.8.9:8080",
                            "zone-a");
                    health.record(new InstanceHealthSignal(
                            ref,
                            InstanceHealthSignalType.SERVER_ERROR,
                            GovernanceCallOutcome.FAILURE,
                            InstanceStatusCodeClass.HTTP_5XX,
                            GovernanceDurationBucket.LT_100_MS,
                            java.time.Instant.now()));

                    GovernanceDiagnosticsAutoConfiguration.GovernanceInstanceHealthEndpoint endpoint =
                            context.getBean(GovernanceDiagnosticsAutoConfiguration.GovernanceInstanceHealthEndpoint.class);

                    assertThat(endpoint.instanceHealth())
                            .singleElement()
                            .satisfies(body -> {
                                assertThat(body).containsKeys(
                                        "instanceRef",
                                        "state",
                                        "quarantineReason",
                                        "recoveryAdvice",
                                        "windowCalls");
                                assertThat(body.toString()).doesNotContain("10.24.8.9", "8080");
                            });
                });
    }

    @Test
    void traceRecorderIsDisabledByDefault() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(GovernanceTraceRecorder.class));
    }

    @Test
    void traceRecorderBindsTypedConfigurationWhenEnabled() {
        contextRunner
                .withPropertyValues(
                        "nexary.governance.trace.enabled=true",
                        "nexary.governance.trace.max-traces=3",
                        "nexary.governance.trace.max-events-per-trace=4",
                        "nexary.governance.trace.ttl=30s",
                        "nexary.governance.trace.expose-external-trace-id=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(GovernanceTraceRecorder.class);
                    GovernanceRuntimeProperties.Trace properties =
                            context.getBean(GovernanceRuntimeProperties.class).getTrace();
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getMaxTraces()).isEqualTo(3);
                    assertThat(properties.getMaxEventsPerTrace()).isEqualTo(4);
                    assertThat(properties.getTtl()).hasSeconds(30);
                    assertThat(properties.isExposeExternalTraceId()).isFalse();
                });
    }

    @Test
    void traceDiagnosticsReturnsStableJsonShapeWhenEnabled() {
        webContextRunner
                .withPropertyValues(
                        "nexary.governance.diagnostics.enabled=true",
                        "nexary.governance.trace.enabled=true")
                .run(context -> {
                    GovernanceRuntime runtime = context.getBean(GovernanceRuntime.class);
                    GovernanceResource resource = GovernanceResource.http("trace-api", "deadline");
                    String result = runtime.execute(
                            GovernanceContext.builder()
                                    .resource(resource)
                                    .deadline(Instant.now().minusMillis(1))
                                    .build(),
                            () -> "primary",
                            () -> "fallback");
                    assertThat(result).isEqualTo("fallback");

                    GovernanceDiagnosticsAutoConfiguration.GovernanceDiagnosticsEndpoint endpoint =
                            context.getBean(GovernanceDiagnosticsAutoConfiguration.GovernanceDiagnosticsEndpoint.class);

                    assertThat(endpoint.traces())
                            .singleElement()
                            .satisfies(body -> {
                                assertThat(body).containsKeys(
                                        "traceKey",
                                        "rootResourceKey",
                                        "terminalOutcome",
                                        "primaryStopReason",
                                        "steps");
                                assertThat(body.get("rootResourceKey")).isEqualTo(resource.key());
                                assertThat(body.get("primaryStopReason")).isEqualTo("DEADLINE_EXPIRED");
                                assertThat(body.toString()).doesNotContain("payload", "stackTrace", "exception");
                            });
                    assertThat(endpoint.faultSummary())
                            .containsEntry("traceCount", 1L)
                            .containsEntry("stoppedCount", 1L);
                });
    }

    @Test
    void cancellationReceiverIsDisabledByDefault() {
        webContextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(GovernanceCancellationAutoConfiguration.CancellationReceiverEndpoint.class));
    }

    @Test
    void cancellationReceiverCancelsLocalTokenWhenEnabled() {
        webContextRunner
                .withPropertyValues("nexary.governance.cancellation.receiver.enabled=true")
                .run(context -> {
                    GovernanceCancellationAutoConfiguration.CancellationReceiverEndpoint endpoint =
                            context.getBean(GovernanceCancellationAutoConfiguration.CancellationReceiverEndpoint.class);
                    CancellationToken token = CancellationToken.create("cancel-boot-test");
                    CancellationContext.callWithToken(token, () -> {
                        endpoint.cancel(java.util.Collections.emptyMap(), java.util.Map.of(
                                "cancellationId", "cancel-boot-test",
                                "reason", "CLIENT_DISCONNECTED"));

                        assertThat(token.isCancelled()).isTrue();
                        assertThat(token.reason()).isEqualTo(CancellationReason.CLIENT_DISCONNECTED);
                        return null;
                    });
                });
    }

    @Test
    void cancellationReceiverRejectsWrongTokenWhenConfigured() {
        webContextRunner
                .withPropertyValues(
                        "nexary.governance.cancellation.receiver.enabled=true",
                        "nexary.governance.cancellation.receiver.token=secret")
                .run(context -> {
                    GovernanceCancellationAutoConfiguration.CancellationReceiverEndpoint endpoint =
                            context.getBean(GovernanceCancellationAutoConfiguration.CancellationReceiverEndpoint.class);

                    assertThat(endpoint.cancel(java.util.Collections.emptyMap(), java.util.Collections.emptyMap()).getStatusCode().value())
                            .isEqualTo(403);
                });
    }
}
