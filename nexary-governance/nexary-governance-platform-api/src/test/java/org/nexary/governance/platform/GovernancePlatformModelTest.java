package org.nexary.governance.platform;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
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

    @Test
    void evidenceRejectsHighCardinalityReferenceKey() {
        assertThrows(IllegalArgumentException.class, () -> new EvidenceItem(
                GovernanceSignalType.LATENCY,
                GovernanceSignalSeverity.WARNING,
                "open-api",
                "open-api-cluster",
                "cn-east",
                "http:open-api:profile",
                "SLOW",
                "GT_2S",
                "Latency evidence",
                "TRACE",
                "trace id with spaces",
                Instant.EPOCH));
    }

    @Test
    void requestFlowKeepsSanitizedTraceEvidence() {
        GovernanceEvidenceRef ref = new GovernanceEvidenceRef(
                GovernanceEvidenceRefType.SKYWALKING_TRACE,
                "sw-flow-room-resource-redis-room",
                "SkyWalking trace",
                "readonly://skywalking/trace/sw-flow-room-resource-redis-room");
        GovernanceSpan span = new GovernanceSpan(
                "flow-room-resource-redis-room-span-1",
                "",
                "room-resource",
                "cache:redis-room:state",
                "redis",
                "allocate state lookup",
                0,
                2760,
                "ERROR",
                "REDIS_TIMEOUT",
                List.of(ref));
        GovernanceRequestFlow flow = new GovernanceRequestFlow(
                "flow-room-resource-redis-room",
                "room-resource",
                "http:room-resource:allocate",
                "room-a",
                "ERROR",
                3180,
                Instant.EPOCH,
                1,
                "REDIS_TIMEOUT",
                "Room resource dependency timeout",
                List.of(span),
                List.of(ref));

        assertEquals("flow-room-resource-redis-room", flow.traceKey());
        assertEquals("redis", flow.spans().get(0).component());
        assertEquals(GovernanceEvidenceRefType.SKYWALKING_TRACE, flow.evidenceRefs().get(0).type());
    }

    @Test
    void hostSignalKeepsWatermarkBuckets() {
        GovernanceHostSignal host = new GovernanceHostSignal(
                "redis-room-a-primary",
                "redis-room",
                "redis-room-cluster",
                "room-a",
                "CRITICAL",
                77,
                94,
                68,
                91,
                2.2,
                0.2,
                2160,
                62,
                0,
                "REDIS_TIMEOUT",
                Instant.EPOCH,
                Map.of("source", "demo"));

        assertEquals("redis-room-a-primary", host.hostKey());
        assertEquals(68.0, host.swapPercent());
        assertEquals("REDIS_TIMEOUT", host.lastError());
    }
}
