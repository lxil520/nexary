package org.nexary.governance.platform;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GovernancePlatformModelTest {
    @Test
    void signalKeepsLowCardinalityFields() {
        GovernanceSignal signal = new GovernanceSignal(
                "cloud-phone",
                "prod-demo",
                "open-api",
                "open-api-cluster",
                "cn-east",
                "http:open-api:profile",
                GovernanceSignalType.ERROR_RATE,
                GovernanceSignalSeverity.WARNING,
                "ERROR",
                "100_250MS",
                Instant.EPOCH,
                Map.of("source", "demo"));

        assertEquals("open-api", signal.serviceKey());
        assertEquals("http:open-api:profile", signal.resourceKey());
        assertEquals("demo", signal.attributes().get("source"));
    }

    @Test
    void signalRejectsForbiddenAttributeKeys() {
        assertThrows(IllegalArgumentException.class, () -> new GovernanceSignal(
                "cloud-phone",
                "prod-demo",
                "open-api",
                "open-api-cluster",
                "cn-east",
                "http:open-api:profile",
                GovernanceSignalType.ERROR_RATE,
                GovernanceSignalSeverity.WARNING,
                "ERROR",
                "100_250MS",
                Instant.EPOCH,
                Map.of("userId", "u-1")));
    }

    @Test
    void assetRejectsUnsupportedTokenCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new GovernanceAsset(
                GovernanceAssetKind.SERVICE,
                "open api",
                "Open API",
                Map.of()));
    }
}
