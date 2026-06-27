package org.nexary.governance.platform.server;

import org.nexary.governance.platform.EvidenceItem;
import org.nexary.governance.platform.GovernanceAsset;
import org.nexary.governance.platform.GovernanceAssetKind;
import org.nexary.governance.platform.GovernanceConnector;
import org.nexary.governance.platform.GovernanceConnectorStatus;
import org.nexary.governance.platform.GovernanceDependency;
import org.nexary.governance.platform.GovernanceDependencyEdge;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceServiceNode;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSignalSeverity;
import org.nexary.governance.platform.GovernanceSignalType;
import org.nexary.governance.platform.GovernanceTopology;
import org.nexary.governance.platform.ImpactScope;
import org.nexary.governance.platform.IncidentCandidate;
import org.nexary.governance.platform.SuggestedCheck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/** Read-only platform service for asset ingestion, topology projection, and incident candidates. */
public final class GovernancePlatformService {
    private final GovernancePlatformRepository repository;

    /** Creates the platform service. */
    public GovernancePlatformService(GovernancePlatformRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** Records a resource report from an SDK, sample, or connector. */
    public void recordResources(GovernancePlatformResourceReport report) {
        repository.saveResourceReport(Objects.requireNonNull(report, "report"));
    }

    /** Records a low-cardinality signal. */
    public void recordSignal(GovernanceSignal signal) {
        repository.saveSignal(Objects.requireNonNull(signal, "signal"));
    }

    /** Returns the current topology projection. */
    public GovernanceTopology topology() {
        List<GovernancePlatformResourceReport> reports = repository.resourceReports();
        List<GovernanceSignal> signals = repository.signals();
        Map<String, GovernanceAsset> assets = mergeAssets(reports);
        return new GovernanceTopology(
                serviceNodes(assets, signals),
                dependencyEdges(reports),
                connectorStatuses(reports));
    }

    /** Returns service nodes sorted by severity and key. */
    public List<GovernanceServiceNode> services() {
        return topology().services();
    }

    /** Returns incident candidates derived from retained warning and critical signals. */
    public List<IncidentCandidate> incidents() {
        Map<String, List<GovernanceSignal>> grouped = repository.signals().stream()
                .filter(signal -> signal.severity() == GovernanceSignalSeverity.WARNING
                        || signal.severity() == GovernanceSignalSeverity.CRITICAL)
                .collect(Collectors.groupingBy(this::incidentGroupKey));
        List<IncidentCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<GovernanceSignal>> entry : grouped.entrySet()) {
            List<GovernanceSignal> signals = entry.getValue();
            GovernanceSignal latest = signals.stream()
                    .max(Comparator.comparing(GovernanceSignal::timestamp))
                    .orElse(null);
            if (latest == null) {
                continue;
            }
            GovernanceSignalSeverity severity = signals.stream()
                    .anyMatch(signal -> signal.severity() == GovernanceSignalSeverity.CRITICAL)
                    ? GovernanceSignalSeverity.CRITICAL : GovernanceSignalSeverity.WARNING;
            List<EvidenceItem> evidence = signals.stream()
                    .sorted(Comparator.comparing(GovernanceSignal::timestamp).reversed())
                    .limit(8)
                    .map(this::evidence)
                    .toList();
            GovernanceSignal primary = signals.stream()
                    .max(this::comparePrimarySignal)
                    .orElse(latest);
            Instant startedAt = signals.stream()
                    .map(GovernanceSignal::timestamp)
                    .min(Comparator.naturalOrder())
                    .orElse(latest.timestamp());
            int resourceCount = (int) signals.stream().map(GovernanceSignal::resourceKey).distinct().count();
            candidates.add(new IncidentCandidate(
                    "incident-" + latest.environmentKey() + "-" + latest.serviceKey() + "-" + latest.clusterKey() + "-" + latest.zoneKey(),
                    incidentTitle(severity, latest),
                    severity,
                    new ImpactScope(latest.serviceKey(), latest.clusterKey(), latest.zoneKey()),
                    evidence,
                    suggestedCheck(primary),
                    startedAt,
                    latest.timestamp(),
                    primary.resourceKey(),
                    signals.size(),
                    resourceCount));
        }
        candidates.sort(Comparator.comparing(IncidentCandidate::lastSeenAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return candidates;
    }

    /** Returns one incident candidate by stable key. */
    public Optional<IncidentCandidate> incident(String incidentKey) {
        if (incidentKey == null || incidentKey.isBlank()) {
            return Optional.empty();
        }
        return incidents().stream()
                .filter(candidate -> candidate.incidentKey().equals(incidentKey))
                .findFirst();
    }

    /** Returns connector statuses sorted by connector key. */
    public List<GovernanceConnectorStatus> connectors() {
        return topology().connectors();
    }

    /** Returns retained signals sorted newest first. */
    public List<GovernanceSignal> signals() {
        return repository.signals().stream()
                .sorted(Comparator.comparing(GovernanceSignal::timestamp).reversed())
                .toList();
    }

    private Map<String, GovernanceAsset> mergeAssets(List<GovernancePlatformResourceReport> reports) {
        Map<String, GovernanceAsset> assets = new LinkedHashMap<>();
        for (GovernancePlatformResourceReport report : reports) {
            for (GovernanceAsset asset : report.assets()) {
                assets.put(asset.key(), asset);
            }
        }
        return assets;
    }

    private List<GovernanceServiceNode> serviceNodes(Map<String, GovernanceAsset> assets, List<GovernanceSignal> signals) {
        Map<String, long[]> counts = new HashMap<>();
        for (GovernanceSignal signal : signals) {
            long[] serviceCounts = counts.computeIfAbsent(signal.serviceKey(), ignored -> new long[2]);
            if (signal.severity() == GovernanceSignalSeverity.WARNING) {
                serviceCounts[0]++;
            }
            if (signal.severity() == GovernanceSignalSeverity.CRITICAL) {
                serviceCounts[1]++;
            }
        }
        List<GovernanceServiceNode> services = new ArrayList<>();
        for (GovernanceAsset asset : assets.values()) {
            if (asset.kind() != GovernanceAssetKind.SERVICE) {
                continue;
            }
            long[] serviceCounts = counts.getOrDefault(asset.key(), new long[2]);
            Map<String, String> attributes = asset.attributes();
            services.add(new GovernanceServiceNode(
                    asset.key(),
                    asset.name(),
                    attributes.getOrDefault("team", "unknown"),
                    attributes.getOrDefault("environment", "unknown"),
                    attributes.getOrDefault("cluster", "unknown"),
                    attributes.getOrDefault("zone", "unknown"),
                    serviceCounts[0],
                    serviceCounts[1],
                    attributes));
        }
        services.sort(Comparator
                .comparingLong(GovernanceServiceNode::criticalCount).reversed()
                .thenComparing(Comparator.comparingLong(GovernanceServiceNode::warningCount).reversed())
                .thenComparing(GovernanceServiceNode::serviceKey));
        return services;
    }

    private List<GovernanceDependencyEdge> dependencyEdges(List<GovernancePlatformResourceReport> reports) {
        Map<String, GovernanceDependency> merged = new LinkedHashMap<>();
        for (GovernancePlatformResourceReport report : reports) {
            for (GovernanceDependency dependency : report.dependencies()) {
                merged.put(dependency.sourceKey() + "->" + dependency.targetKey() + ":" + dependency.resourceKey(), dependency);
            }
        }
        Map<String, long[]> countsByResource = signalCountsByResource(repository.signals());
        return merged.values().stream()
                .map(dependency -> new GovernanceDependencyEdge(
                        dependency.sourceKey(),
                        dependency.targetKey(),
                        dependency.kind(),
                        dependency.resourceKey(),
                        countsByResource.getOrDefault(dependency.resourceKey(), new long[2])[0],
                        countsByResource.getOrDefault(dependency.resourceKey(), new long[2])[1],
                        dependency.attributes()))
                .sorted(Comparator.comparing(GovernanceDependencyEdge::sourceKey).thenComparing(GovernanceDependencyEdge::targetKey))
                .toList();
    }

    private List<GovernanceConnectorStatus> connectorStatuses(List<GovernancePlatformResourceReport> reports) {
        Map<String, GovernanceConnector> merged = new LinkedHashMap<>();
        for (GovernancePlatformResourceReport report : reports) {
            for (GovernanceConnector connector : report.connectors()) {
                merged.put(connector.connectorKey(), connector);
            }
        }
        return merged.values().stream()
                .map(connector -> new GovernanceConnectorStatus(
                        connector.connectorKey(),
                        connector.kind(),
                        connector.state(),
                        connector.displayName(),
                        connector.lastMessage(),
                        Instant.now()))
                .sorted(Comparator.comparing(GovernanceConnectorStatus::connectorKey))
                .toList();
    }

    private EvidenceItem evidence(GovernanceSignal signal) {
        return new EvidenceItem(
                signal.signalType(),
                signal.severity(),
                signal.serviceKey(),
                signal.clusterKey(),
                signal.zoneKey(),
                signal.resourceKey(),
                signal.outcome(),
                signal.durationBucket(),
                evidenceMessage(signal),
                referenceType(signal),
                referenceKey(signal),
                signal.timestamp());
    }

    private String incidentGroupKey(GovernanceSignal signal) {
        return signal.environmentKey() + ":" + signal.serviceKey() + ":" + signal.clusterKey() + ":" + signal.zoneKey();
    }

    private String incidentTitle(GovernanceSignalSeverity severity, GovernanceSignal signal) {
        return severity.name() + " signals on " + signal.serviceKey();
    }

    private SuggestedCheck suggestedCheck(GovernanceSignal signal) {
        return new SuggestedCheck(signal.resourceKey(), suggestedMessage(signal.signalType()));
    }

    private String suggestedMessage(GovernanceSignalType signalType) {
        return switch (signalType) {
            case QUARANTINE_CANDIDATE, INSTANCE_SUSPECT -> "Check abnormal instance evidence before changing traffic";
            case GATEWAY_DISCONNECT -> "Check gateway disconnect and upstream timeout evidence";
            case SENTINEL_BLOCK -> "Check Sentinel block count and mapped resource policy";
            case RETRY_STOPPED -> "Check retry-stop evidence and downstream recovery";
            case CANCELLATION -> "Check client disconnect or expired deadline evidence";
            case LATENCY -> "Check latency evidence and downstream dependency";
            case ERROR_RATE -> "Check error-rate evidence and impacted dependency";
            case ACTIVE_REQUESTS -> "Check active request pressure and bulkhead state";
            case REQUEST_RATE -> "Check traffic spike and resource limits";
            case RESOURCE_EVENT -> "Check retained resource event evidence";
        };
    }

    private String evidenceMessage(GovernanceSignal signal) {
        return signal.signalType().name() + " / " + signal.outcome() + " / " + signal.durationBucket();
    }

    private String referenceType(GovernanceSignal signal) {
        return switch (signal.signalType()) {
            case SENTINEL_BLOCK -> "SENTINEL_RESOURCE";
            case GATEWAY_DISCONNECT -> "GATEWAY_ROUTE";
            case LATENCY, ERROR_RATE, ACTIVE_REQUESTS, REQUEST_RATE -> "METRIC_QUERY";
            case QUARANTINE_CANDIDATE, INSTANCE_SUSPECT -> "INSTANCE_HEALTH";
            case RETRY_STOPPED -> "FAULT_TRACE";
            case CANCELLATION -> "CANCELLATION_EVENT";
            case RESOURCE_EVENT -> "RESOURCE";
        };
    }

    private String referenceKey(GovernanceSignal signal) {
        return signal.attributes().getOrDefault("reference", signal.resourceKey());
    }

    private int comparePrimarySignal(GovernanceSignal left, GovernanceSignal right) {
        int severity = Integer.compare(severityWeight(left.severity()), severityWeight(right.severity()));
        if (severity != 0) {
            return severity;
        }
        int type = Integer.compare(signalPriority(left.signalType()), signalPriority(right.signalType()));
        if (type != 0) {
            return type;
        }
        return left.timestamp().compareTo(right.timestamp());
    }

    private int severityWeight(GovernanceSignalSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 3;
            case WARNING -> 2;
            case INFO -> 1;
        };
    }

    private int signalPriority(GovernanceSignalType signalType) {
        return switch (signalType) {
            case QUARANTINE_CANDIDATE -> 100;
            case INSTANCE_SUSPECT -> 90;
            case GATEWAY_DISCONNECT -> 80;
            case SENTINEL_BLOCK -> 75;
            case RETRY_STOPPED -> 70;
            case CANCELLATION -> 65;
            case ERROR_RATE -> 60;
            case LATENCY -> 55;
            case ACTIVE_REQUESTS -> 50;
            case REQUEST_RATE -> 40;
            case RESOURCE_EVENT -> 10;
        };
    }

    private Map<String, long[]> signalCountsByResource(List<GovernanceSignal> signals) {
        Map<String, long[]> counts = new HashMap<>();
        for (GovernanceSignal signal : signals) {
            long[] resourceCounts = counts.computeIfAbsent(signal.resourceKey(), ignored -> new long[2]);
            if (signal.severity() == GovernanceSignalSeverity.WARNING) {
                resourceCounts[0]++;
            }
            if (signal.severity() == GovernanceSignalSeverity.CRITICAL) {
                resourceCounts[1]++;
            }
        }
        return counts;
    }
}
