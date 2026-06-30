package org.nexary.governance.platform.server;

import org.junit.jupiter.api.Test;
import org.nexary.governance.platform.GovernanceAsset;
import org.nexary.governance.platform.GovernanceAssetKind;
import org.nexary.governance.platform.GovernanceDependency;
import org.nexary.governance.platform.GovernanceDependencyKind;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSignalSeverity;
import org.nexary.governance.platform.GovernanceSignalType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernancePlatformServiceTest {
    @Test
    void buildsTopologyAndIncidentCandidates() {
        GovernancePlatformService service = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());
        service.recordResources(new GovernancePlatformResourceReport(
                List.of(new GovernanceAsset(GovernanceAssetKind.SERVICE, "open-api", "Open API",
                        Map.of("team", "platform", "environment", "prod", "cluster", "open-api-cluster", "zone", "cn-east"))),
                List.of(new GovernanceDependency("open-api", "redis-main", GovernanceDependencyKind.CACHE, "cache:open-api:profile", Map.of())),
                List.of()));
        service.recordSignal(new GovernanceSignal(
                "workspace",
                "prod",
                "open-api",
                "open-api-cluster",
                "cn-east",
                "cache:open-api:profile",
                GovernanceSignalType.ERROR_RATE,
                GovernanceSignalSeverity.WARNING,
                "ERROR",
                "100_250MS",
                Instant.EPOCH,
                Map.of("source", "test")));

        assertEquals(1, service.topology().services().size());
        assertEquals(1, service.topology().dependencies().size());
        assertEquals(1, service.incidents().size());
        assertEquals("open-api", service.incidents().get(0).impactScope().serviceKey());
        assertEquals("cache:open-api:profile", service.incidents().get(0).primaryResourceKey());
        assertEquals(1, service.topology().dependencies().get(0).warningCount());
    }

    @Test
    void groupsIncidentEvidenceByServiceClusterAndZone() {
        GovernancePlatformService service = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());
        service.recordSignal(signal("open-api", "http:open-api:profile", GovernanceSignalType.ERROR_RATE, GovernanceSignalSeverity.WARNING, "ERROR", Instant.EPOCH));
        service.recordSignal(signal("open-api", "gateway:open-api:disconnect", GovernanceSignalType.GATEWAY_DISCONNECT, GovernanceSignalSeverity.WARNING, "DISCONNECTED", Instant.EPOCH.plusSeconds(1)));
        service.recordSignal(signal("open-api", "downstream:open-api:profile", GovernanceSignalType.QUARANTINE_CANDIDATE, GovernanceSignalSeverity.CRITICAL, "SUSPECT", Instant.EPOCH.plusSeconds(2)));

        assertEquals(1, service.incidents().size());
        assertEquals(3, service.incidents().get(0).evidenceCount());
        assertEquals(3, service.incidents().get(0).impactedResourceCount());
        assertEquals("downstream:open-api:profile", service.incidents().get(0).primaryResourceKey());
        assertEquals("INSTANCE_HEALTH", service.incidents().get(0).evidence().get(0).referenceType());
        assertEquals("open-api", service.incidents().get(0).evidence().get(0).serviceKey());
        assertEquals(1, service.incident(service.incidents().get(0).incidentKey()).stream().count());
    }

    @Test
    void separatesExplicitIncidentEvidencePackages() {
        GovernancePlatformService service = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());
        service.recordSignal(signal(
                "signaling",
                "cache:signaling:room-state",
                GovernanceSignalType.REDIS_TIMEOUT,
                GovernanceSignalSeverity.CRITICAL,
                "TIMEOUT",
                Instant.EPOCH,
                Map.of("incidentKey", "incident-signaling-redis-timeout", "flowKey", "flow-signaling-redis-timeout")));
        service.recordSignal(signal(
                "signaling",
                "cache:signaling:pipeline",
                GovernanceSignalType.REDIS_PIPELINE_ERROR,
                GovernanceSignalSeverity.WARNING,
                "PIPELINE_ERROR",
                Instant.EPOCH.plusSeconds(1),
                Map.of("incidentKey", "incident-signaling-redis-pipeline", "flowKey", "flow-signaling-redis-pipeline")));
        service.recordSignal(signal(
                "signaling",
                "downstream:signaling:board-callback",
                GovernanceSignalType.BROKEN_PIPE,
                GovernanceSignalSeverity.WARNING,
                "CLIENT_ABORT",
                Instant.EPOCH.plusSeconds(2),
                Map.of("incidentKey", "incident-signaling-board-broken-pipe", "flowKey", "flow-signaling-board-broken-pipe")));

        assertEquals(3, service.incidents().size());
        assertEquals(3, service.requestFlows().size());
        assertEquals("REDIS_TIMEOUT", service.requestFlow("flow-signaling-redis-timeout").orElseThrow().primaryError());
        assertEquals(3, service.transactions().size());
    }

    @Test
    void snapshotIncludesRequestFlowsTransactionsAndHosts() {
        GovernancePlatformService service = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());
        service.recordResources(new GovernancePlatformResourceReport(
                List.of(new GovernanceAsset(GovernanceAssetKind.INSTANCE, "room-a-signaling-01", "room-a-signaling-01",
                        Map.of("service", "signaling", "cluster", "signaling-cluster", "zone", "room-a", "state", "WARNING"))),
                List.of(),
                List.of()));
        service.recordSignal(signal(
                "signaling",
                "cache:signaling:room-state",
                GovernanceSignalType.REDIS_TIMEOUT,
                GovernanceSignalSeverity.CRITICAL,
                "TIMEOUT",
                Instant.EPOCH,
                Map.of("flowKey", "flow-signaling-redis-timeout", "metricTotal", "10", "metricFailure", "2")));

        Map<String, Object> snapshot = service.snapshot();

        assertEquals(1, service.hosts().size());
        assertEquals(1, ((List<?>) snapshot.get("requestFlows")).size());
        assertEquals(1, ((List<?>) snapshot.get("transactions")).size());
        assertEquals(1, ((List<?>) snapshot.get("hosts")).size());
    }

    @Test
    void snapshotExposesDataModeAndFreshness() {
        GovernancePlatformService empty = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());

        Map<String, Object> emptySnapshot = empty.snapshot();

        assertEquals("UNAVAILABLE", emptySnapshot.get("sourceMode"));
        assertTrue(((Map<?, ?>) emptySnapshot.get("freshness")).containsKey("generatedAt"));

        GovernancePlatformService demo = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());
        demo.recordSignal(signal(
                "signaling",
                "cache:signaling:room-state",
                GovernanceSignalType.REDIS_TIMEOUT,
                GovernanceSignalSeverity.CRITICAL,
                "TIMEOUT",
                Instant.now(),
                Map.of("source", "demo", "flowKey", "flow-demo")));

        assertEquals("DEMO", demo.snapshot().get("sourceMode"));
    }

    @Test
    void requestFlowQueryFiltersSortsAndPaginates() {
        GovernancePlatformService service = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());
        service.recordSignal(signal(
                "open-api",
                "cache:open-api:profile",
                GovernanceSignalType.REDIS_TIMEOUT,
                GovernanceSignalSeverity.CRITICAL,
                "TIMEOUT",
                Instant.EPOCH.plusSeconds(2),
                Map.of(
                        "flowKey", "flow-open-api-redis",
                        "durationMs", "2200",
                        "endpoint", "GET:/profile",
                        "targetResource", "cache:redis:profile",
                        "skywalkingRef", "sw-open-api-redis")));
        service.recordSignal(signal(
                "open-api",
                "http:open-api:health",
                GovernanceSignalType.RESOURCE_EVENT,
                GovernanceSignalSeverity.INFO,
                "OK",
                Instant.EPOCH.plusSeconds(3),
                Map.of(
                        "flowKey", "flow-open-api-health",
                        "durationMs", "20",
                        "endpoint", "GET:/health")));

        List<?> flows = service.requestFlows(
                null,
                null,
                "open-api",
                "profile",
                "ERROR",
                1000L,
                "redis",
                "skywalking",
                "duration_desc");

        assertEquals(1, flows.size());
        assertEquals("flow-open-api-redis", service.requestFlows(null, null, null, null, null, null, null, null, "duration_desc").get(0).traceKey());
    }

    @Test
    void includesInfoMetricSignalsInTransactionsWithoutCreatingIncidents() {
        GovernancePlatformService service = new GovernancePlatformService(new InMemoryGovernancePlatformRepository());
        service.recordSignal(signal(
                "signaling",
                "cache:signaling:live-probe",
                GovernanceSignalType.RESOURCE_EVENT,
                GovernanceSignalSeverity.INFO,
                "OK",
                Instant.EPOCH,
                Map.of(
                        "flowKey", "flow-live-redis-probe",
                        "endpoint", "probe:redis:set-get",
                        "metricTotal", "20",
                        "metricFailure", "0",
                        "metricQps", "40.0",
                        "metricP95Ms", "8")));

        assertEquals(0, service.incidents().size());
        assertEquals(1, service.transactions().size());
        assertEquals("probe:redis:set-get", service.transactions().get(0).endpointKey());
        assertTrue(service.requestFlow("flow-live-redis-probe").isPresent());
    }

    private GovernanceSignal signal(
            String serviceKey,
            String resourceKey,
            GovernanceSignalType signalType,
            GovernanceSignalSeverity severity,
            String outcome,
            Instant timestamp) {
        return new GovernanceSignal(
                "workspace",
                "prod",
                serviceKey,
                serviceKey + "-cluster",
                "cn-east",
                resourceKey,
                signalType,
                severity,
                outcome,
                "100_250MS",
                timestamp,
                Map.of("source", "test"));
    }

    private GovernanceSignal signal(
            String serviceKey,
            String resourceKey,
            GovernanceSignalType signalType,
            GovernanceSignalSeverity severity,
            String outcome,
            Instant timestamp,
            Map<String, String> attributes) {
        Map<String, String> copy = new java.util.LinkedHashMap<>();
        copy.put("source", "test");
        copy.putAll(attributes);
        return new GovernanceSignal(
                "workspace",
                "prod",
                serviceKey,
                serviceKey + "-cluster",
                "cn-east",
                resourceKey,
                signalType,
                severity,
                outcome,
                "100_250MS",
                timestamp,
                copy);
    }
}
