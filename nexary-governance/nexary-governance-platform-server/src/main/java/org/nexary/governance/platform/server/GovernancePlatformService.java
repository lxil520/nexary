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
                .collect(Collectors.groupingBy(signal -> signal.serviceKey() + ":" + signal.resourceKey()));
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
                    .limit(6)
                    .map(this::evidence)
                    .toList();
            candidates.add(new IncidentCandidate(
                    "incident-" + latest.serviceKey() + "-" + latest.resourceKey(),
                    latest.signalType().name() + " on " + latest.serviceKey(),
                    severity,
                    new ImpactScope(latest.serviceKey(), latest.clusterKey(), latest.zoneKey()),
                    evidence,
                    new SuggestedCheck(latest.resourceKey(), "Check resource evidence before changing policy"),
                    latest.timestamp()));
        }
        candidates.sort(Comparator.comparing(IncidentCandidate::lastSeenAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return candidates;
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
        return merged.values().stream()
                .map(dependency -> new GovernanceDependencyEdge(
                        dependency.sourceKey(),
                        dependency.targetKey(),
                        dependency.kind(),
                        dependency.resourceKey(),
                        0,
                        0,
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
                signal.resourceKey(),
                signal.outcome(),
                signal.timestamp());
    }
}
