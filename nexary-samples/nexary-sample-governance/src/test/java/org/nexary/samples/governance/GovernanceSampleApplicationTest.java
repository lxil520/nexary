package org.nexary.samples.governance;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.CancellationHeaders;
import org.nexary.governance.runtime.GovernanceCircuitState;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.GovernanceRejectionReason;
import org.nexary.samples.governance.common.CircuitProfileResult;
import org.nexary.samples.governance.api.GovernanceSampleController;
import org.nexary.samples.governance.config.GovernanceSampleConfiguration;
import org.nexary.samples.governance.service.LocalCircuitBreakerProfileGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        classes = org.nexary.samples.governance.app.GovernanceSampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.profiles.active=instance-health")
@Import(GovernanceSampleApplicationTest.MetricsTestConfiguration.class)
class GovernanceSampleApplicationTest {
    @Autowired
    private GovernanceRuntime governanceRuntime;

    @Autowired
    private GovernanceSampleController controller;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetCircuit() {
        controller.resetCircuit();
    }

    @Test
    void startsWithGovernanceRuntime() {
        assertThat(governanceRuntime).isNotNull();
    }

    @Test
    void recordsGovernanceObservationMetersWithBoundedTags() throws Exception {
        controller.profile("u-1");
        controller.profile("u-2");
        controller.profile("u-3");

        assertThat(meterRegistry.find("nexary.observation.events.total")
                .tag("category", "governance")
                .tag("operation", "governance.rate_limited")
                .tag("resource_kind", "http")
                .tag("governance_action", "rate_limited")
                .tag("traffic_channel", "online")
                .tag("traffic_priority", "normal")
                .counter()).isNotNull();
        assertThat(meterRegistry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("resource"))
                        .noneMatch(tag -> tag.getKey().equals("tenant"))
                        .noneMatch(tag -> tag.getKey().equals("biz_key"))
                        .noneMatch(tag -> tag.getKey().equals("user_id")));
    }

    @Test
    void demonstratesCircuitOpenFallbackHalfOpenAndRecovery() throws Exception {
        assertThat(controller.circuitProfile("u-10", "success").getCircuitState())
                .isEqualTo(GovernanceCircuitState.CLOSED);

        controller.circuitProfile("u-11", "failure");
        CircuitProfileResult opened = controller.circuitProfile("u-12", "failure");

        assertThat(opened.getCircuitState()).isEqualTo(GovernanceCircuitState.OPEN);
        assertThat(opened.getOutcome()).isEqualTo("failure_opened");

        CircuitProfileResult rejected = controller.circuitProfile("u-13", "success");

        assertThat(rejected.getSource()).isEqualTo("fallback");
        assertThat(rejected.getOutcome()).isEqualTo("fallback_open");
        assertThat(rejected.getLastRejectionReason()).isEqualTo(GovernanceRejectionReason.CIRCUIT_OPEN);

        Thread.sleep(LocalCircuitBreakerProfileGateway.OPEN_STATE_DURATION.plusMillis(50).toMillis());
        CircuitProfileResult recovered = controller.circuitProfile("u-14", "success");

        assertThat(recovered.getCircuitState()).isEqualTo(GovernanceCircuitState.CLOSED);
        assertThat(recovered.getOutcome()).isEqualTo("half_open_recovered");
        assertThat(controller.circuitState().windowCalls()).isZero();
    }

    @Test
    void reopensCircuitWhenHalfOpenProbeFails() throws Exception {
        controller.circuitProfile("u-20", "failure");
        controller.circuitProfile("u-21", "failure");

        Thread.sleep(LocalCircuitBreakerProfileGateway.OPEN_STATE_DURATION.plusMillis(50).toMillis());
        CircuitProfileResult reopened = controller.circuitProfile("u-22", "failure");

        assertThat(reopened.getCircuitState()).isEqualTo(GovernanceCircuitState.OPEN);
        assertThat(reopened.getOutcome()).isEqualTo("half_open_reopened");
    }

    @Test
    void opensCircuitAfterRepeatedSlowCalls() throws Exception {
        controller.circuitProfile("u-30", "slow");
        CircuitProfileResult opened = controller.circuitProfile("u-31", "slow");

        assertThat(opened.getCircuitState()).isEqualTo(GovernanceCircuitState.OPEN);
        assertThat(opened.getWindowSlowCalls()).isEqualTo(2);
        assertThat(opened.getOutcome()).isEqualTo("slow_opened");
    }

    @Test
    void exposesReadOnlyDiagnosticsEndpoints() throws Exception {
        controller.profile("u-40");
        controller.profile("u-41");
        controller.profile("u-42");
        controller.circuitProfile("u-43", "failure");
        controller.circuitProfile("u-44", "failure");

        String summary = restTemplate.getForObject("/nexary/governance/summary", String.class);
        String resources = restTemplate.getForObject("/nexary/governance/resources", String.class);
        String events = restTemplate.getForObject("/nexary/governance/events", String.class);

        assertThat(summary)
                .contains("\"resourceCount\"")
                .contains("\"rejectedCount\"")
                .contains("\"openCircuitCount\"");
        assertThat(resources)
                .contains("\"resourceKey\"")
                .contains("\"policySnapshot\"")
                .contains("\"runtimeSnapshot\"")
                .contains("profile-api")
                .contains("profile-service");
        assertThat(events)
                .contains("\"action\"")
                .contains("\"outcome\"")
                .contains("\"rejectionReason\"")
                .contains("\"durationBucket\"")
                .doesNotContain("u-40", "u-41", "u-42", "u-43", "u-44");
    }

    @Test
    void cancellationHeadersStopSampleWorkAndRemainLowCardinality() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(CancellationHeaders.CANCELLATION_ID, "sample-cancel-hidden-id");
        headers.add(CancellationHeaders.CANCEL_REASON, "CLIENT_DISCONNECTED");

        ResponseEntity<String> response = restTemplate.exchange(
                "/governance/cancellation/slow/u-50?durationMillis=3000",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getBody()).contains("fallback");

        String summary = restTemplate.getForObject("/nexary/governance/summary", String.class);
        String events = restTemplate.getForObject("/nexary/governance/events", String.class);

        assertThat(summary).contains("\"cancelledCount\"");
        assertThat(events)
                .contains("\"cancellationReason\":\"CLIENT_DISCONNECTED\"")
                .doesNotContain("sample-cancel-hidden-id", "u-50");
    }

    @Test
    void instanceHealthProfileDetectsLocalQuarantineCandidate() {
        ResponseEntity<String> scenario = restTemplate.postForEntity(
                "/governance/instance-health/scenario",
                null,
                String.class);
        String health = restTemplate.getForObject("/nexary/governance/instance-health", String.class);
        String resourceHealth = restTemplate.getForObject(
                "/nexary/governance/instance-health/" + GovernanceSampleConfiguration.INSTANCE_HEALTH_RESOURCE.key(),
                String.class);
        String summary = restTemplate.getForObject("/nexary/governance/summary", String.class);
        String events = restTemplate.getForObject("/nexary/governance/events", String.class);

        assertThat(scenario.getBody())
                .contains("\"scenario\":\"scenario\"")
                .contains("instance-a")
                .contains("instance-c")
                .contains("QUARANTINE_CANDIDATE");
        assertThat(health)
                .contains("instance-a")
                .contains("instance-b")
                .contains("instance-c")
                .contains("SLOW_RATIO")
                .doesNotContain("10.", "stackTrace", "payload");
        assertThat(health).containsAnyOf("SERVER_ERROR_RATIO", "READ_TIMEOUT_SPIKE");
        assertThat(resourceHealth).contains("QUARANTINE_CANDIDATE");
        assertThat(summary)
                .contains("\"instanceSuspectCount\"")
                .contains("\"quarantineCandidateCount\"");
        assertThat(events)
                .contains("\"action\":\"QUARANTINE_CANDIDATE\"")
                .contains("\"quarantineReason\"")
                .doesNotContain("stackTrace", "payload");
    }

    @TestConfiguration
    static class MetricsTestConfiguration {
        @Bean
        MeterRegistry governanceSampleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
