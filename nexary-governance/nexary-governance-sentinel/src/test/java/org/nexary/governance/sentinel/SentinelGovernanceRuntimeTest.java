package org.nexary.governance.sentinel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.context.CancellationToken;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceIsolationReason;
import org.nexary.core.governance.GovernancePriority;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.GovernanceTrafficClass;
import org.nexary.core.retry.RetryStopReason;
import org.nexary.governance.runtime.GovernanceBlockReason;
import org.nexary.governance.runtime.GovernanceCallOutcome;
import org.nexary.governance.runtime.GovernanceEngine;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernanceRejectedException;
import org.nexary.governance.runtime.GovernanceRejectionReason;
import org.nexary.governance.runtime.LocalGovernancePolicyRegistry;

class SentinelGovernanceRuntimeTest {
    @Test
    void rejectsWithSentinelRateLimitAndKeepsLowCardinalityDiagnostics() throws Exception {
        GovernanceResource resource = GovernanceResource.http("sentinel-api", "query");
        SentinelGovernanceRuntime runtime = new SentinelGovernanceRuntime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .maxRequestsPerWindow(1)
                                .rateLimitWindow(Duration.ofSeconds(60))
                                .build())
                        .build(),
                null);
        GovernanceContext context = GovernanceContext.builder().resource(resource).build();

        assertThat(runtime.execute(context, () -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> runtime.execute(context, () -> "blocked"))
                .isInstanceOf(GovernanceRejectedException.class);

        assertThat(runtime.summary().blockedCount()).isEqualTo(1);
        assertThat(runtime.summary().retryStoppedCount()).isEqualTo(1);
        assertThat(runtime.summary().sentinelResourceCount()).isEqualTo(1);
        assertThat(runtime.recentEvents().get(1).engine()).isEqualTo(GovernanceEngine.SENTINEL);
        assertThat(runtime.recentEvents().get(1).blockReason()).isEqualTo(GovernanceBlockReason.RATE_LIMITED);
        assertThat(runtime.recentEvents().get(1).retryStopReason()).isEqualTo(RetryStopReason.RATE_LIMITED);
        assertThat(runtime.recentEvents().get(1).rejectionReason()).isEqualTo(GovernanceRejectionReason.RATE_LIMITED);
        assertThat(runtime.snapshots().get(0).lastBlockReason()).isEqualTo(GovernanceBlockReason.RATE_LIMITED);
        assertThat(runtime.snapshots().get(0).lastRetryStopReason()).isEqualTo(RetryStopReason.RATE_LIMITED);
    }

    @Test
    void cancellationHappensBeforeSentinelEntry() {
        GovernanceResource resource = GovernanceResource.http("cancelled-api", "query");
        SentinelGovernanceRuntime runtime = new SentinelGovernanceRuntime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePolicy.builder()
                                .maxRequestsPerWindow(1)
                                .rateLimitWindow(Duration.ofSeconds(60))
                                .build())
                        .build(),
                null);
        CancellationToken token = CancellationToken.create("not-reported");
        token.cancel(CancellationReason.UPSTREAM_CANCELLED);
        GovernanceContext context = GovernanceContext.builder()
                .resource(resource)
                .cancellationToken(token)
                .build();

        assertThatThrownBy(() -> runtime.execute(context, () -> "cancelled"))
                .isInstanceOf(GovernanceRejectedException.class);

        assertThat(runtime.summary().cancelledCount()).isEqualTo(1);
        assertThat(runtime.summary().retryStoppedCount()).isEqualTo(1);
        assertThat(runtime.summary().blockedCount()).isZero();
        assertThat(runtime.recentEvents().get(0).outcome()).isEqualTo(GovernanceCallOutcome.CANCELLED);
        assertThat(runtime.recentEvents().get(0).blockReason()).isEqualTo(GovernanceBlockReason.NONE);
        assertThat(runtime.recentEvents().get(0).cancellationReason()).isEqualTo(CancellationReason.UPSTREAM_CANCELLED);
        assertThat(runtime.recentEvents().get(0).retryStopReason()).isEqualTo(RetryStopReason.UPSTREAM_CANCELLED);
    }

    @Test
    void priorityIsolationHappensBeforeSharedSentinelResourceRules() throws Exception {
        GovernanceResource resource = GovernanceResource.http("sentinel-priority-api", "query");
        SentinelGovernanceRuntime runtime = new SentinelGovernanceRuntime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(resource, GovernancePriority.LOW, GovernancePolicy.builder()
                                .maxRequestsPerWindow(1)
                                .rateLimitWindow(Duration.ofSeconds(60))
                                .build())
                        .policy(resource, GovernancePriority.HIGH, GovernancePolicy.allowAll())
                        .build(),
                null);
        GovernanceContext lowBatch = GovernanceContext.builder()
                .resource(resource)
                .trafficTag(TrafficTag.builder()
                        .channel(TrafficTag.Channel.BATCH)
                        .priority(TrafficTag.Priority.LOW)
                        .build())
                .build();
        GovernanceContext highOnline = GovernanceContext.builder()
                .resource(resource)
                .trafficTag(TrafficTag.builder()
                        .channel(TrafficTag.Channel.ONLINE)
                        .priority(TrafficTag.Priority.HIGH)
                        .build())
                .build();

        assertThat(runtime.execute(lowBatch, () -> "batch-1")).isEqualTo("batch-1");
        assertThat(runtime.execute(lowBatch, () -> "batch-main", () -> "batch-fallback")).isEqualTo("batch-fallback");
        assertThat(runtime.execute(highOnline, () -> "online")).isEqualTo("online");

        assertThat(runtime.summary().isolatedCount()).isEqualTo(1);
        assertThat(runtime.summary().blockedCount()).isEqualTo(1);
        assertThat(runtime.recentEvents())
                .anySatisfy(event -> {
                    assertThat(event.engine()).isEqualTo(GovernanceEngine.SENTINEL);
                    assertThat(event.trafficClass()).isEqualTo(GovernanceTrafficClass.BATCH);
                    assertThat(event.priority()).isEqualTo(GovernancePriority.LOW);
                    assertThat(event.outcome()).isEqualTo(GovernanceCallOutcome.ISOLATED);
                    assertThat(event.isolationReason()).isEqualTo(GovernanceIsolationReason.PRIORITY_RATE_LIMITED);
                    assertThat(event.blockReason()).isEqualTo(GovernanceBlockReason.RATE_LIMITED);
                })
                .anySatisfy(event -> {
                    assertThat(event.trafficClass()).isEqualTo(GovernanceTrafficClass.ONLINE);
                    assertThat(event.priority()).isEqualTo(GovernancePriority.HIGH);
                    assertThat(event.outcome()).isEqualTo(GovernanceCallOutcome.SUCCESS);
                });
    }
}
