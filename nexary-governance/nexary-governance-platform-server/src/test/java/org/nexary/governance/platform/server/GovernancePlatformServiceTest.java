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
}
