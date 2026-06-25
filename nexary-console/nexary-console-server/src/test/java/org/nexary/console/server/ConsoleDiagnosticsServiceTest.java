package org.nexary.console.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nexary.console.api.ConsoleEventsResponse;
import org.nexary.console.api.ConsoleResourceItem;
import org.nexary.console.api.ConsoleResourcesResponse;
import org.nexary.console.api.ConsoleSummaryResponse;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.governance.runtime.GovernanceCallOutcome;
import org.nexary.governance.runtime.GovernanceCircuitState;
import org.nexary.governance.runtime.GovernanceDiagnostics;
import org.nexary.governance.runtime.GovernanceDurationBucket;
import org.nexary.governance.runtime.GovernancePolicySnapshot;
import org.nexary.governance.runtime.GovernanceRejectionReason;
import org.nexary.governance.runtime.GovernanceResourceDescriptor;
import org.nexary.governance.runtime.GovernanceRuntimeAction;
import org.nexary.governance.runtime.GovernanceRuntimeEvent;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.GovernanceRuntimeSummary;

class ConsoleDiagnosticsServiceTest {
    private static final Instant EVENT_TIME = Instant.parse("2026-06-24T12:30:00Z");
    private static final String RESOURCE_ID = "http:profile-api:nexary:get-profile";

    @Test
    void returnsEmptyResponsesWhenDiagnosticsHasNoResources() {
        ConsoleDiagnosticsService service = new ConsoleDiagnosticsService(new StubDiagnostics(
                Collections.emptyList(),
                Collections.emptyList(),
                new GovernanceRuntimeSummary(0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, null)));

        ConsoleSummaryResponse summary = service.summary();
        ConsoleResourcesResponse resources = service.resources();
        ConsoleEventsResponse events = service.events();

        assertThat(summary.getResourceCount()).isZero();
        assertThat(summary.getEventCount()).isZero();
        assertThat(resources.getItems()).isEmpty();
        assertThat(events.getItems()).isEmpty();
    }

    @Test
    void mapsSingleResourceAndRecentEvents() {
        ConsoleDiagnosticsService service = new ConsoleDiagnosticsService(sampleDiagnostics());

        ConsoleResourceItem resource = service.resource(RESOURCE_ID).orElse(null);
        ConsoleResourcesResponse resources = service.resources();
        ConsoleEventsResponse events = service.events();

        assertThat(resources.getItems()).hasSize(1);
        assertThat(resource).isNotNull();
        assertThat(resource.getResourceKey()).isEqualTo(RESOURCE_ID);
        assertThat(resource.getKind()).isEqualTo("HTTP");
        assertThat(resource.getPolicySnapshot().getRateLimitWindow()).isEqualTo("PT1S");
        assertThat(resource.getPolicySnapshot().getFailureRateThreshold()).isEqualTo(50.0d);
        assertThat(resource.getRuntimeSnapshot().getCircuitState()).isEqualTo("OPEN");
        assertThat(resource.getRuntimeSnapshot().getWindowFailures()).isEqualTo(2);
        assertThat(resource.getRuntimeSnapshot().getLastRejectionReason()).isEqualTo("CIRCUIT_OPEN");
        assertThat(resource.getRuntimeSnapshot().getLastCancellationReason()).isEqualTo("CLIENT_DISCONNECTED");
        assertThat(resource.getRuntimeSnapshot().getMaxConcurrency()).isEqualTo(2);
        assertThat(resource.getRuntimeSnapshot().getRateLimitWindow()).isEqualTo("PT1S");
        assertThat(events.getItems()).hasSize(1);
        assertThat(events.getItems().get(0).getOutcome()).isEqualTo("REJECTED");
        assertThat(events.getItems().get(0).getCancellationReason()).isEqualTo("CLIENT_DISCONNECTED");
        assertThat(events.getItems().get(0).getDurationBucket()).isEqualTo("NOT_RUN");
    }

    @Test
    void returnsEmptyWhenResourceDoesNotExist() {
        ConsoleDiagnosticsService service = new ConsoleDiagnosticsService(sampleDiagnostics());

        assertThat(service.resource("http:missing:nexary:get")).isEmpty();
    }

    @Test
    void serializedJsonContainsOnlyLowCardinalityConsoleFields() throws Exception {
        ConsoleDiagnosticsService service = new ConsoleDiagnosticsService(sampleDiagnostics());
        ObjectMapper objectMapper = new ObjectMapper();

        String resources = objectMapper.writeValueAsString(service.resources());
        String events = objectMapper.writeValueAsString(service.events());

        assertThat(resources)
                .contains("\"resourceKey\"", "\"kind\"", "\"operation\"", "\"policySnapshot\"", "\"runtimeSnapshot\"")
                .doesNotContain("payload")
                .doesNotContain("tenant")
                .doesNotContain("bizKey")
                .doesNotContain("messageBody")
                .doesNotContain("exceptionMessage")
                .doesNotContain("stackTrace");
        assertThat(events)
                .contains("\"resourceKey\"", "\"action\"", "\"outcome\"", "\"durationBucket\"", "\"cancellationReason\"")
                .doesNotContain("cancellationId")
                .doesNotContain("payload")
                .doesNotContain("tenant")
                .doesNotContain("bizKey")
                .doesNotContain("messageBody")
                .doesNotContain("exceptionMessage")
                .doesNotContain("stackTrace");
    }

    private static GovernanceDiagnostics sampleDiagnostics() {
        GovernancePolicySnapshot policy = new GovernancePolicySnapshot(
                Duration.ofMillis(250),
                10,
                Duration.ofSeconds(1),
                2,
                false,
                2,
                50.0d,
                25.0d,
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                1,
                8,
                Duration.ofSeconds(5),
                2);
        GovernanceRuntimeSnapshot runtime = new GovernanceRuntimeSnapshot(
                RESOURCE_ID,
                "normal",
                GovernanceCircuitState.OPEN,
                4,
                2,
                1,
                2,
                3L,
                GovernanceRejectionReason.CIRCUIT_OPEN,
                CancellationReason.CLIENT_DISCONNECTED,
                EVENT_TIME.plusSeconds(30),
                1,
                2,
                10,
                Duration.ofSeconds(1),
                false,
                2,
                50.0d,
                25.0d,
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                1,
                8,
                Duration.ofSeconds(5),
                2,
                EVENT_TIME.minusSeconds(1),
                GovernanceCallOutcome.REJECTED,
                EVENT_TIME);
        GovernanceResourceDescriptor descriptor = new GovernanceResourceDescriptor(
                RESOURCE_ID,
                GovernanceResource.ResourceKind.HTTP,
                "profile-api",
                "nexary",
                "get-profile",
                "normal",
                policy,
                runtime);
        GovernanceRuntimeEvent event = new GovernanceRuntimeEvent(
                RESOURCE_ID,
                GovernanceRuntimeAction.REJECT,
                GovernanceCallOutcome.REJECTED,
                GovernanceRejectionReason.CIRCUIT_OPEN,
                CancellationReason.CLIENT_DISCONNECTED,
                GovernanceCircuitState.OPEN,
                EVENT_TIME,
                GovernanceDurationBucket.NOT_RUN);
        return new StubDiagnostics(
                Collections.singletonList(descriptor),
                Collections.singletonList(event),
                new GovernanceRuntimeSummary(1, 1, 1, 0L, 0L, 1L, 0L, 1L, 1L, 0L, 0L, EVENT_TIME));
    }

    private static final class StubDiagnostics implements GovernanceDiagnostics {
        private final List<GovernanceResourceDescriptor> resources;
        private final List<GovernanceRuntimeEvent> events;
        private final GovernanceRuntimeSummary summary;

        private StubDiagnostics(
                List<GovernanceResourceDescriptor> resources,
                List<GovernanceRuntimeEvent> events,
                GovernanceRuntimeSummary summary) {
            this.resources = resources;
            this.events = events;
            this.summary = summary;
        }

        @Override
        public List<GovernanceResourceDescriptor> resources() {
            return resources;
        }

        @Override
        public List<GovernanceRuntimeSnapshot> snapshots() {
            return Arrays.asList(resources.get(0).runtimeSnapshot());
        }

        @Override
        public List<GovernanceRuntimeEvent> recentEvents() {
            return events;
        }

        @Override
        public GovernanceRuntimeSummary summary() {
            return summary;
        }
    }
}
