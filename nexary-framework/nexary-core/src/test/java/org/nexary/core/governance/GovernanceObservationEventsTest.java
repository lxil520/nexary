package org.nexary.core.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.fault.FaultSignal;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.retry.RetrySignal;
import org.nexary.core.retry.RetryStopReason;

class GovernanceObservationEventsTest {
    @Test
    void deadlineExceededUsesGovernanceCategoryAndBoundedTags() {
        Instant startedAt = Instant.parse("2026-06-19T00:00:00Z");
        Instant endedAt = startedAt.plusMillis(15);
        TrafficTag trafficTag = TrafficTag.builder()
                .channel(TrafficTag.Channel.ONLINE)
                .priority(TrafficTag.Priority.CRITICAL)
                .tenant("tenant-should-not-be-a-meter-tag")
                .bizKey("order-123")
                .build();

        NexaryObservationEvent event = GovernanceObservationEvents.deadlineExceeded(
                GovernanceResource.service("checkout-api"), trafficTag, startedAt, endedAt);

        assertThat(event.category()).isEqualTo(NexaryObservationEvent.EventCategory.GOVERNANCE);
        assertThat(event.operation()).isEqualTo("governance.deadline.exceeded");
        assertThat(event.duration()).isEqualTo(Duration.ofMillis(15));
        assertThat(event.faultSignal().type()).isEqualTo(FaultSignal.FaultType.TIMEOUT);
        assertThat(event.tags()).containsOnly(
                Map.entry("resource_kind", "service"),
                Map.entry("resource", "checkout-api"),
                Map.entry("provider", "nexary"),
                Map.entry("operation", "default"),
                Map.entry("traffic_channel", "online"),
                Map.entry("traffic_priority", "critical"),
                Map.entry("governance_action", "deadline_exceeded"),
                Map.entry("outcome", "rejected"),
                Map.entry("failure_category", "timeout"));
    }

    @Test
    void retryStoppedBucketsAttemptsWithoutLeakingReasonText() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        RetrySignal signal = RetrySignal.retry(7, Duration.ofMillis(100), "raw downstream reason");

        NexaryObservationEvent event = GovernanceObservationEvents.retryStopped(
                GovernanceResource.messaging("payment-events", "kafka"),
                TrafficTag.defaults(),
                signal,
                now,
                now);

        assertThat(event.tags()).contains(
                Map.entry("governance_action", "retry_stopped"),
                Map.entry("retry_decision", "stop"),
                Map.entry("retry_phase", "stopped"),
                Map.entry("retry_stop_reason", "none"),
                Map.entry("retry_attempt_bucket", "6_10"));
        assertThat(event.tags()).doesNotContainKey("reason");
    }

    @Test
    void retryStoppedUsesBoundedStopReason() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");

        NexaryObservationEvent event = GovernanceObservationEvents.retryStopped(
                GovernanceResource.messaging("payment-events", "kafka"),
                TrafficTag.defaults(),
                RetrySignal.stop(RetryStopReason.CIRCUIT_OPEN),
                now,
                now);

        assertThat(event.tags()).contains(
                Map.entry("retry_decision", "stop"),
                Map.entry("retry_stop_reason", "circuit_open"),
                Map.entry("retry_attempt_bucket", "0"));
        assertThat(event.tags()).doesNotContainKey("reason");
    }

    @Test
    void runtimeRejectionsUseDistinctActions() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");

        assertThat(GovernanceObservationEvents.rateLimited(null, null, now, now).tags())
                .contains(Map.entry("governance_action", "rate_limited"));
        assertThat(GovernanceObservationEvents.degraded(null, null, now, now).tags())
                .contains(Map.entry("governance_action", "degraded"));
        assertThat(GovernanceObservationEvents.bulkheadRejected(null, null, now, now).tags())
                .contains(Map.entry("governance_action", "bulkhead_rejected"));
        assertThat(GovernanceObservationEvents.circuitOpen(null, null, "half_open_limited", now, now).tags())
                .contains(
                        Map.entry("governance_action", "half_open_limited"),
                        Map.entry("failure_category", "circuit_open"));
    }
}
