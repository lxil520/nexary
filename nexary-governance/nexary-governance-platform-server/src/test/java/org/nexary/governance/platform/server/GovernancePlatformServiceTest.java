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
    }
}
