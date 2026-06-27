package org.nexary.samples.governance.platform;

import org.nexary.governance.platform.GovernanceAsset;
import org.nexary.governance.platform.GovernanceAssetKind;
import org.nexary.governance.platform.GovernanceConnector;
import org.nexary.governance.platform.GovernanceConnectorKind;
import org.nexary.governance.platform.GovernanceConnectorState;
import org.nexary.governance.platform.GovernanceDependency;
import org.nexary.governance.platform.GovernanceDependencyKind;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSignalSeverity;
import org.nexary.governance.platform.GovernanceSignalType;
import org.nexary.governance.platform.server.GovernancePlatformService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Seeds a cloud-phone-style abstract topology for the platform sample. */
@Component
public final class GovernancePlatformDemoData implements ApplicationRunner {
    private final GovernancePlatformService platformService;

    /** Creates the demo data seeder. */
    public GovernancePlatformDemoData(GovernancePlatformService platformService) {
        this.platformService = platformService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** Re-seeds resource and signal data. */
    public void seed() {
        platformService.recordResources(new GovernancePlatformResourceReport(assets(), dependencies(), connectors()));
        Instant now = Instant.now();
        platformService.recordSignal(signal("open-api", "open-api-cluster", "cn-east", "http:open-api:profile", GovernanceSignalType.ERROR_RATE, GovernanceSignalSeverity.WARNING, "ERROR", "100_250MS", now.minusSeconds(90)));
        platformService.recordSignal(signal("open-api", "open-api-cluster", "cn-east", "downstream:open-api:profile", GovernanceSignalType.LATENCY, GovernanceSignalSeverity.WARNING, "SLOW", "GT_2S", now.minusSeconds(82), "promql-open-api-p95"));
        platformService.recordSignal(signal("open-api", "open-api-cluster", "cn-east", "sentinel:open-api:profile", GovernanceSignalType.SENTINEL_BLOCK, GovernanceSignalSeverity.CRITICAL, "BLOCKED", "LT_50MS", now.minusSeconds(78), "sentinel-open-api-profile"));
        platformService.recordSignal(signal("sdk-api", "sdk-api-cluster", "cn-east", "gateway:sdk-api:disconnect", GovernanceSignalType.GATEWAY_DISCONNECT, GovernanceSignalSeverity.WARNING, "DISCONNECTED", "LT_50MS", now.minusSeconds(70)));
        platformService.recordSignal(signal("consumer", "consumer-cluster", "cn-east", "mq:consumer:retry-stop", GovernanceSignalType.RETRY_STOPPED, GovernanceSignalSeverity.WARNING, "STOPPED", "NOT_RUN", now.minusSeconds(60)));
        platformService.recordSignal(signal("room-resource", "room-resource-cluster", "room-a", "downstream:room-resource:allocate", GovernanceSignalType.INSTANCE_SUSPECT, GovernanceSignalSeverity.WARNING, "SUSPECT", "GT_2S", now.minusSeconds(42), "instance-health-room-resource"));
        platformService.recordSignal(signal("room-resource", "room-resource-cluster", "room-a", "downstream:room-resource:allocate", GovernanceSignalType.QUARANTINE_CANDIDATE, GovernanceSignalSeverity.CRITICAL, "SUSPECT", "GT_2S", now.minusSeconds(30), "instance-health-room-resource"));
    }

    private List<GovernanceAsset> assets() {
        return List.of(
                asset(GovernanceAssetKind.WORKSPACE, "cloud-phone", "Cloud Phone Demo", Map.of()),
                asset(GovernanceAssetKind.ENVIRONMENT, "prod-demo", "Production Demo", Map.of("workspace", "cloud-phone")),
                asset(GovernanceAssetKind.TEAM, "platform-team", "Platform Team", Map.of("environment", "prod-demo")),
                service("open-api", "Open API", "open-api-cluster", "cn-east"),
                service("sdk-api", "SDK API", "sdk-api-cluster", "cn-east"),
                service("scheduler", "Scheduler", "scheduler-cluster", "cn-east"),
                service("consumer", "Consumer", "consumer-cluster", "cn-east"),
                service("admin", "Admin Console", "admin-cluster", "cn-east"),
                service("user-platform", "User Platform", "user-platform-cluster", "cn-east"),
                service("room-resource", "Room Resource", "room-resource-cluster", "room-a"),
                service("signaling", "Signaling", "signaling-cluster", "room-a"),
                middleware("redis-main", "Redis Main", "cn-east"),
                middleware("pg-primary", "Postgres Primary", "cn-east"),
                middleware("oss-main", "Object Storage", "cn-east"),
                middleware("redis-room", "Room Redis", "room-a"));
    }

    private List<GovernanceDependency> dependencies() {
        return List.of(
                dependency("open-api", "sdk-api", GovernanceDependencyKind.HTTP, "http:open-api:sdk-api"),
                dependency("open-api", "user-platform", GovernanceDependencyKind.HTTP, "downstream:open-api:profile"),
                dependency("open-api", "redis-main", GovernanceDependencyKind.CACHE, "cache:open-api:profile"),
                dependency("open-api", "pg-primary", GovernanceDependencyKind.DATABASE, "jdbc:open-api:account"),
                dependency("sdk-api", "signaling", GovernanceDependencyKind.SIGNALING, "signal:sdk-api:connect"),
                dependency("scheduler", "room-resource", GovernanceDependencyKind.JOB, "job:scheduler:repair-room"),
                dependency("consumer", "redis-main", GovernanceDependencyKind.MESSAGING, "mq:consumer:retry-stop"),
                dependency("consumer", "oss-main", GovernanceDependencyKind.OBJECT_STORAGE, "oss:consumer:snapshot"),
                dependency("room-resource", "redis-room", GovernanceDependencyKind.CACHE, "cache:room-resource:state"),
                dependency("signaling", "room-resource", GovernanceDependencyKind.RESOURCE, "downstream:signaling:room-resource"));
    }

    private List<GovernanceConnector> connectors() {
        return List.of(
                new GovernanceConnector("nexary-sdk-demo", GovernanceConnectorKind.NEXARY_SDK, GovernanceConnectorState.HEALTHY, "Nexary SDK", "demo signals received", Map.of("mode", "demo")),
                new GovernanceConnector("micrometer-demo", GovernanceConnectorKind.MICROMETER, GovernanceConnectorState.HEALTHY, "Micrometer", "local metrics ready", Map.of("mode", "demo")),
                new GovernanceConnector("sentinel-readonly-demo", GovernanceConnectorKind.SENTINEL, GovernanceConnectorState.DEGRADED, "Sentinel", "read-only demo data", Map.of("mode", "demo")),
                new GovernanceConnector("im-dry-run-demo", GovernanceConnectorKind.FEISHU, GovernanceConnectorState.DISABLED, "Feishu Dry Run", "test message only", Map.of("mode", "dry-run")));
    }

    private GovernanceAsset service(String key, String name, String cluster, String zone) {
        return asset(GovernanceAssetKind.SERVICE, key, name, Map.of(
                "team", "platform-team",
                "environment", "prod-demo",
                "cluster", cluster,
                "zone", zone));
    }

    private GovernanceAsset middleware(String key, String name, String zone) {
        return asset(GovernanceAssetKind.MIDDLEWARE, key, name, Map.of(
                "environment", "prod-demo",
                "zone", zone));
    }

    private GovernanceAsset asset(GovernanceAssetKind kind, String key, String name, Map<String, String> attributes) {
        return new GovernanceAsset(kind, key, name, attributes);
    }

    private GovernanceDependency dependency(String source, String target, GovernanceDependencyKind kind, String resourceKey) {
        return new GovernanceDependency(source, target, kind, resourceKey, Map.of("environment", "prod-demo"));
    }

    private GovernanceSignal signal(
            String service,
            String cluster,
            String zone,
            String resourceKey,
            GovernanceSignalType type,
            GovernanceSignalSeverity severity,
            String outcome,
            String durationBucket,
            Instant timestamp) {
        return new GovernanceSignal(
                "cloud-phone",
                "prod-demo",
                service,
                cluster,
                zone,
                resourceKey,
                type,
                severity,
                outcome,
                durationBucket,
                timestamp,
                Map.of("source", "demo"));
    }

    private GovernanceSignal signal(
            String service,
            String cluster,
            String zone,
            String resourceKey,
            GovernanceSignalType type,
            GovernanceSignalSeverity severity,
            String outcome,
            String durationBucket,
            Instant timestamp,
            String reference) {
        return new GovernanceSignal(
                "cloud-phone",
                "prod-demo",
                service,
                cluster,
                zone,
                resourceKey,
                type,
                severity,
                outcome,
                durationBucket,
                timestamp,
                Map.of("source", "demo", "reference", reference));
    }
}
