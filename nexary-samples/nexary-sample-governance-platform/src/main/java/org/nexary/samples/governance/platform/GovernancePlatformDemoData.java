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

import static java.util.Map.entry;

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
        platformService.recordSignal(signal("signaling", "signaling-cluster", "room-a", "cache:signaling:room-state",
                GovernanceSignalType.REDIS_TIMEOUT, GovernanceSignalSeverity.CRITICAL, "TIMEOUT", "GT_2S", now.minusSeconds(120),
                Map.ofEntries(
                        entry("incidentKey", "incident-signaling-redis-timeout"),
                        entry("incidentTitle", "Redis timeout on signaling room state"),
                        entry("flowKey", "flow-signaling-redis-timeout"),
                        entry("endpoint", "http:signaling:heartbeat"),
                        entry("targetService", "redis-room"),
                        entry("targetResource", "cache:redis-room:state"),
                        entry("component", "redis"),
                        entry("operation", "GET room state"),
                        entry("durationMs", "2860"),
                        entry("spanDurationMs", "2310"),
                        entry("summary", "Redis command timeout with swap and disk IO pressure"),
                        entry("metricTotal", "184"),
                        entry("metricFailure", "21"),
                        entry("metricFailureRate", "0.114"),
                        entry("metricP95Ms", "2260"),
                        entry("metricP99Ms", "3410"),
                        entry("skywalkingRef", "sw-flow-signaling-redis-timeout"),
                        entry("catRef", "cat-signaling-redis-room"),
                        entry("promqlRef", "prom-redis-room-swap-io"),
                        entry("logRef", "log-signaling-redis-timeout"),
                        entry("sbaRef", "sba-redis-room-a-primary"))));
        platformService.recordSignal(signal("redis-room", "redis-room-cluster", "room-a", "host:redis-room-a-primary",
                GovernanceSignalType.HOST_WATERMARK, GovernanceSignalSeverity.CRITICAL, "SATURATED", "GT_2S", now.minusSeconds(118),
                Map.ofEntries(
                        entry("incidentKey", "incident-signaling-redis-timeout"),
                        entry("flowKey", "flow-signaling-redis-timeout"),
                        entry("endpoint", "host:redis-room-a-primary"),
                        entry("targetService", "redis-room"),
                        entry("targetResource", "host:redis-room-a-primary"),
                        entry("component", "host"),
                        entry("operation", "swap and disk IO waterline"),
                        entry("durationMs", "2860"),
                        entry("spanDurationMs", "2860"),
                        entry("summary", "Redis host memory pressure is spilling to swap"),
                        entry("promqlRef", "prom-redis-room-host-watermark"),
                        entry("sbaRef", "sba-redis-room-a-primary"))));
        platformService.recordSignal(signal("signaling", "signaling-cluster", "room-a", "cache:signaling:pipeline",
                GovernanceSignalType.REDIS_PIPELINE_ERROR, GovernanceSignalSeverity.WARNING, "PIPELINE_ERROR", "LT_1S", now.minusSeconds(92),
                Map.ofEntries(
                        entry("incidentKey", "incident-signaling-redis-pipeline"),
                        entry("incidentTitle", "Redis pipeline errors on signaling"),
                        entry("flowKey", "flow-signaling-redis-pipeline"),
                        entry("endpoint", "http:signaling:batch-sync"),
                        entry("targetService", "redis-room"),
                        entry("targetResource", "cache:redis-room:pipeline"),
                        entry("component", "redis"),
                        entry("operation", "pipeline close"),
                        entry("durationMs", "820"),
                        entry("spanDurationMs", "610"),
                        entry("summary", "Redis pipeline returned invalid command bucket"),
                        entry("metricTotal", "64"),
                        entry("metricFailure", "5"),
                        entry("metricFailureRate", "0.078"),
                        entry("metricP95Ms", "760"),
                        entry("metricP99Ms", "1180"),
                        entry("skywalkingRef", "sw-flow-signaling-redis-pipeline"),
                        entry("catRef", "cat-signaling-redis-pipeline"),
                        entry("logRef", "log-signaling-redis-pipeline"))));
        platformService.recordSignal(signal("signaling", "signaling-cluster", "room-a", "downstream:signaling:board-callback",
                GovernanceSignalType.BROKEN_PIPE, GovernanceSignalSeverity.WARNING, "CLIENT_ABORT", "LT_1S", now.minusSeconds(80),
                Map.ofEntries(
                        entry("incidentKey", "incident-signaling-board-broken-pipe"),
                        entry("incidentTitle", "Board callback broken pipe on signaling"),
                        entry("flowKey", "flow-signaling-board-broken-pipe"),
                        entry("endpoint", "http:signaling:instance-callback"),
                        entry("targetService", "board-service"),
                        entry("targetResource", "http:board-service:callback"),
                        entry("component", "http"),
                        entry("operation", "callback write"),
                        entry("durationMs", "940"),
                        entry("spanDurationMs", "870"),
                        entry("summary", "Board service became unreachable or closed the response"),
                        entry("metricTotal", "47"),
                        entry("metricFailure", "4"),
                        entry("metricFailureRate", "0.085"),
                        entry("metricP95Ms", "920"),
                        entry("metricP99Ms", "1480"),
                        entry("skywalkingRef", "sw-flow-signaling-board-broken-pipe"),
                        entry("catRef", "cat-signaling-board-callback"),
                        entry("logRef", "log-signaling-board-broken-pipe"),
                        entry("gatewayRef", "gateway-board-callback"))));
        platformService.recordSignal(signal("open-api", "open-api-cluster", "cn-east", "http:open-api:allocate",
                GovernanceSignalType.PACKET_LOSS, GovernanceSignalSeverity.WARNING, "JITTER", "GT_1S", now.minusSeconds(65),
                Map.ofEntries(
                        entry("incidentKey", "incident-cloud-room-cross-zone"),
                        entry("incidentTitle", "Cross-zone path jitter from open-api to room-resource"),
                        entry("flowKey", "flow-openapi-room-cross-zone"),
                        entry("endpoint", "http:open-api:allocate"),
                        entry("targetService", "room-resource"),
                        entry("targetResource", "downstream:room-resource:allocate"),
                        entry("component", "network"),
                        entry("operation", "cross-zone allocate"),
                        entry("durationMs", "1680"),
                        entry("spanDurationMs", "1320"),
                        entry("summary", "Cross-zone request path has packet loss and p99 growth"),
                        entry("metricTotal", "145"),
                        entry("metricFailure", "6"),
                        entry("metricFailureRate", "0.041"),
                        entry("metricP95Ms", "1680"),
                        entry("metricP99Ms", "2860"),
                        entry("skywalkingRef", "sw-flow-openapi-room-cross-zone"),
                        entry("catRef", "cat-openapi-allocate"),
                        entry("promqlRef", "prom-cross-zone-network"))));
        platformService.recordSignal(signal("room-resource", "room-resource-cluster", "room-a", "cache:room-resource:state",
                GovernanceSignalType.DEPENDENCY_TIMEOUT, GovernanceSignalSeverity.CRITICAL, "TIMEOUT", "GT_2S", now.minusSeconds(58),
                Map.ofEntries(
                        entry("incidentKey", "incident-room-resource-redis-room"),
                        entry("incidentTitle", "Room resource blocked by room Redis"),
                        entry("flowKey", "flow-room-resource-redis-room"),
                        entry("endpoint", "http:room-resource:allocate"),
                        entry("targetService", "redis-room"),
                        entry("targetResource", "cache:redis-room:state"),
                        entry("component", "redis"),
                        entry("operation", "allocate state lookup"),
                        entry("durationMs", "3180"),
                        entry("spanDurationMs", "2760"),
                        entry("summary", "Room resource dependency timeout on room Redis"),
                        entry("metricTotal", "96"),
                        entry("metricFailure", "18"),
                        entry("metricFailureRate", "0.188"),
                        entry("metricP95Ms", "2840"),
                        entry("metricP99Ms", "4210"),
                        entry("skywalkingRef", "sw-flow-room-resource-redis-room"),
                        entry("catRef", "cat-room-resource-redis-room"),
                        entry("promqlRef", "prom-room-resource-redis-room"),
                        entry("logRef", "log-room-resource-redis-timeout"))));
    }

    private List<GovernanceAsset> assets() {
        return List.of(
                asset(GovernanceAssetKind.WORKSPACE, "cloud-phone", "Cloud Phone Demo", Map.of()),
                asset(GovernanceAssetKind.ENVIRONMENT, "prod-demo", "Production Demo", Map.of("workspace", "cloud-phone")),
                asset(GovernanceAssetKind.TEAM, "platform-team", "Platform Team", Map.of("environment", "prod-demo")),
                service("open-api", "Open API", "open-api-cluster", "cn-east", "145", "0.026", "620", "1340", "12", "68", "72", "74", "OPEN", "WATCH", "4.2", "1.4", "0.022"),
                service("sdk-api", "SDK API", "sdk-api-cluster", "cn-east", "212", "0.011", "260", "740", "16", "55", "63", "61", "CLOSED", "WATCH", "3.6", "0.8", "0.010"),
                service("scheduler", "Scheduler", "scheduler-cluster", "cn-east", "18", "0.002", "180", "360", "4", "32", "41", "39", "CLOSED", "HEALTHY", "2.1", "0.1", "0.002"),
                service("consumer", "Consumer", "consumer-cluster", "cn-east", "64", "0.018", "420", "920", "8", "58", "66", "70", "CLOSED", "HEALTHY", "2.8", "0.3", "0.014"),
                service("admin", "Admin Console", "admin-cluster", "cn-east", "26", "0.004", "160", "320", "3", "38", "45", "44", "CLOSED", "HEALTHY", "2.0", "0.1", "0.003"),
                service("user-platform", "User Platform", "user-platform-cluster", "cn-east", "88", "0.006", "210", "480", "6", "44", "52", "50", "CLOSED", "HEALTHY", "2.4", "0.2", "0.004"),
                service("room-resource", "Room Resource", "room-resource-cluster", "room-a", "96", "0.091", "1800", "3200", "18", "86", "82", "91", "CLOSED", "DEGRADED", "18.5", "2.8", "0.083"),
                service("signaling", "Signaling", "signaling-cluster", "room-a", "184", "0.017", "380", "900", "14", "73", "69", "76", "CLOSED", "DEGRADED", "16.2", "1.9", "0.019"),
                service("board-service", "Board Service", "board-service-cluster", "room-a", "47", "0.085", "920", "1480", "9", "62", "59", "70", "CLOSED", "WATCH", "14.5", "1.1", "0.030"),
                middleware("redis-main", "Redis Main", "cache", "cn-east", "61", "18", "0.004", "HEALTHY"),
                middleware("pg-primary", "Postgres Primary", "database", "cn-east", "67", "42", "0.006", "HEALTHY"),
                middleware("oss-main", "Object Storage", "object-storage", "cn-east", "58", "64", "0.009", "HEALTHY"),
                middleware("redis-room", "Room Redis", "cache", "room-a", "92", "230", "0.114", "CRITICAL"),
                instance("open-api-01", "open-api", "open-api-cluster", "cn-east", "WARNING", "68", "72", "8", "44", "4.2", "1.4", "860", "148", "42", "PACKET_LOSS"),
                instance("sdk-api-01", "sdk-api", "sdk-api-cluster", "cn-east", "WARNING", "55", "63", "3", "30", "3.6", "0.8", "740", "126", "24", "GATEWAY_WATCH"),
                instance("signaling-room-a-01", "signaling", "signaling-cluster", "room-a", "WARNING", "73", "69", "12", "52", "16.2", "1.9", "1280", "184", "56", "BROKEN_PIPE"),
                instance("room-resource-a-01", "room-resource", "room-resource-cluster", "room-a", "CRITICAL", "86", "82", "24", "71", "18.5", "2.8", "1640", "214", "88", "DEPENDENCY_TIMEOUT"),
                instance("redis-room-a-primary", "redis-room", "redis-room-cluster", "room-a", "CRITICAL", "77", "94", "68", "91", "2.2", "0.2", "2160", "62", "0", "REDIS_TIMEOUT"));
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
                dependency("signaling", "room-resource", GovernanceDependencyKind.RESOURCE, "downstream:signaling:room-resource"),
                dependency("signaling", "redis-room", GovernanceDependencyKind.CACHE, "cache:signaling:room-state"),
                dependency("signaling", "board-service", GovernanceDependencyKind.HTTP, "http:board-service:callback"));
    }

    private List<GovernanceConnector> connectors() {
        return List.of(
                new GovernanceConnector("nexary-sdk-demo", GovernanceConnectorKind.NEXARY_SDK, GovernanceConnectorState.HEALTHY, "Nexary SDK", "demo signals received", Map.of("mode", "demo")),
                new GovernanceConnector("micrometer-demo", GovernanceConnectorKind.MICROMETER, GovernanceConnectorState.HEALTHY, "Micrometer", "local metrics ready", Map.of("mode", "demo")),
                new GovernanceConnector("prometheus-readonly-demo", GovernanceConnectorKind.PROMETHEUS, GovernanceConnectorState.HEALTHY, "Prometheus", "watermarks query demo", Map.of("mode", "demo")),
                new GovernanceConnector("alertmanager-readonly-demo", GovernanceConnectorKind.ALERTMANAGER, GovernanceConnectorState.DEGRADED, "Alertmanager", "readonly alert stream", Map.of("mode", "demo")),
                new GovernanceConnector("skywalking-readonly-demo", GovernanceConnectorKind.SKYWALKING, GovernanceConnectorState.HEALTHY, "SkyWalking", "service trace evidence demo", Map.of("mode", "demo")),
                new GovernanceConnector("sentinel-readonly-demo", GovernanceConnectorKind.SENTINEL, GovernanceConnectorState.DEGRADED, "Sentinel", "read-only demo data", Map.of("mode", "demo")),
                new GovernanceConnector("gateway-readonly-demo", GovernanceConnectorKind.GATEWAY, GovernanceConnectorState.DEGRADED, "Spring Cloud Gateway", "route health demo", Map.of("mode", "demo")),
                new GovernanceConnector("feishu-dry-run-demo", GovernanceConnectorKind.FEISHU, GovernanceConnectorState.DISABLED, "Feishu Dry Run", "critical incident test message only", Map.of("mode", "dry-run", "dryRun", "true", "targetTeam", "platform-team", "minSeverity", "CRITICAL")),
                new GovernanceConnector("dingtalk-dry-run-demo", GovernanceConnectorKind.DINGTALK, GovernanceConnectorState.DISABLED, "DingTalk Dry Run", "warning route prepared", Map.of("mode", "dry-run", "dryRun", "true", "targetTeam", "ops-team", "minSeverity", "WARNING")));
    }

    private GovernanceAsset service(
            String key,
            String name,
            String cluster,
            String zone,
            String qps,
            String errorRate,
            String p95Ms,
            String p99Ms,
            String instances,
            String cpuPercent,
            String memoryPercent,
            String watermarkPercent,
            String sentinelState,
            String gatewayState,
            String networkJitterMs,
            String packetLossPercent,
            String httpFailureRate) {
        return asset(GovernanceAssetKind.SERVICE, key, name, Map.ofEntries(
                entry("team", "platform-team"),
                entry("environment", "prod-demo"),
                entry("cluster", cluster),
                entry("zone", zone),
                entry("qps", qps),
                entry("errorRate", errorRate),
                entry("p95Ms", p95Ms),
                entry("p99Ms", p99Ms),
                entry("instances", instances),
                entry("cpuPercent", cpuPercent),
                entry("memoryPercent", memoryPercent),
                entry("watermarkPercent", watermarkPercent),
                entry("sentinelState", sentinelState),
                entry("gatewayState", gatewayState),
                entry("networkJitterMs", networkJitterMs),
                entry("packetLossPercent", packetLossPercent),
                entry("httpFailureRate", httpFailureRate)));
    }

    private GovernanceAsset middleware(String key, String name, String kind, String zone, String usagePercent, String latencyMs, String errorRate, String state) {
        return asset(GovernanceAssetKind.MIDDLEWARE, key, name, Map.of(
                "environment", "prod-demo",
                "kind", kind,
                "zone", zone,
                "usagePercent", usagePercent,
                "latencyMs", latencyMs,
                "errorRate", errorRate,
                "state", state));
    }

    private GovernanceAsset instance(
            String key,
            String service,
            String cluster,
            String zone,
            String state,
            String cpuPercent,
            String memoryPercent,
            String swapPercent,
            String diskIoPercent,
            String networkJitterMs,
            String packetLossPercent,
            String connectionCount,
            String jvmThreadCount,
            String gcPauseMs,
            String lastError) {
        return asset(GovernanceAssetKind.INSTANCE, key, key, Map.ofEntries(
                entry("service", service),
                entry("cluster", cluster),
                entry("zone", zone),
                entry("state", state),
                entry("cpuPercent", cpuPercent),
                entry("memoryPercent", memoryPercent),
                entry("swapPercent", swapPercent),
                entry("diskIoPercent", diskIoPercent),
                entry("networkJitterMs", networkJitterMs),
                entry("packetLossPercent", packetLossPercent),
                entry("connectionCount", connectionCount),
                entry("jvmThreadCount", jvmThreadCount),
                entry("gcPauseMs", gcPauseMs),
                entry("lastError", lastError)));
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
            Map<String, String> attributes) {
        Map<String, String> mergedAttributes = new java.util.LinkedHashMap<>();
        mergedAttributes.put("source", "demo");
        mergedAttributes.putAll(attributes);
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
                mergedAttributes);
    }
}
