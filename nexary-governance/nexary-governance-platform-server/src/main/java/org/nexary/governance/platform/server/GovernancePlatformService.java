package org.nexary.governance.platform.server;

import org.nexary.governance.platform.EvidenceItem;
import org.nexary.governance.platform.GovernanceAsset;
import org.nexary.governance.platform.GovernanceAssetKind;
import org.nexary.governance.platform.GovernanceConnector;
import org.nexary.governance.platform.GovernanceConnectorStatus;
import org.nexary.governance.platform.GovernanceDependency;
import org.nexary.governance.platform.GovernanceDependencyEdge;
import org.nexary.governance.platform.GovernanceEvidenceRef;
import org.nexary.governance.platform.GovernanceEvidenceRefType;
import org.nexary.governance.platform.GovernanceHostSignal;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceRequestFlow;
import org.nexary.governance.platform.GovernanceServiceNode;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSignalSeverity;
import org.nexary.governance.platform.GovernanceSignalType;
import org.nexary.governance.platform.GovernanceSpan;
import org.nexary.governance.platform.GovernanceTopology;
import org.nexary.governance.platform.GovernanceTransactionMetric;
import org.nexary.governance.platform.ImpactScope;
import org.nexary.governance.platform.IncidentCandidate;
import org.nexary.governance.platform.SuggestedCheck;

import java.time.Duration;
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
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

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

    /** Returns a dashboard-ready overview projection for the operations workbench. */
    public Map<String, Object> overview() {
        List<GovernancePlatformResourceReport> reports = repository.resourceReports();
        List<GovernanceSignal> retainedSignals = repository.signals();
        Map<String, GovernanceAsset> assets = mergeAssets(reports);
        List<GovernanceServiceNode> serviceNodes = serviceNodes(assets, retainedSignals);
        List<GovernanceDependencyEdge> dependencies = dependencyEdges(reports);
        List<GovernanceConnectorStatus> connectorStatuses = connectorStatuses(reports);
        List<IncidentCandidate> incidentCandidates = incidents();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("summary", overviewSummary(assets, serviceNodes, dependencies, connectorStatuses, incidentCandidates, retainedSignals));
        overview.put("serviceWatermarks", serviceWatermarks(serviceNodes, incidentCandidates));
        overview.put("zoneWatermarks", zoneWatermarks(serviceNodes));
        overview.put("middlewareWatermarks", middlewareWatermarks(assets, dependencies));
        overview.put("policyPlans", policyPlans(incidentCandidates));
        overview.put("notificationRoutes", notificationRoutes(reports, incidentCandidates));
        return overview;
    }

    /** Returns the complete read-only platform snapshot for the console. */
    public Map<String, Object> snapshot() {
        List<GovernancePlatformResourceReport> reports = repository.resourceReports();
        List<GovernanceSignal> retainedSignals = repository.signals();
        List<GovernanceConnectorStatus> connectorStatuses = connectorStatuses(reports);
        Instant generatedAt = Instant.now();
        String sourceMode = dataSourceMode(reports, retainedSignals, connectorStatuses, generatedAt);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("sourceMode", sourceMode);
        snapshot.put("generatedAt", generatedAt);
        snapshot.put("freshness", dataFreshness(retainedSignals, connectorStatuses, generatedAt, sourceMode));
        snapshot.put("dataSources", dataSources(connectorStatuses, retainedSignals, sourceMode));
        snapshot.put("warnings", dataWarnings(sourceMode, connectorStatuses, retainedSignals));
        snapshot.put("overview", overview());
        snapshot.put("topology", PlatformJson.topology(topology()));
        snapshot.put("services", services().stream().map(PlatformJson::service).toList());
        snapshot.put("incidents", incidents().stream().map(PlatformJson::incident).toList());
        snapshot.put("connectors", connectorStatuses.stream().map(PlatformJson::connector).toList());
        snapshot.put("signals", signals().stream().map(PlatformJson::signal).toList());
        snapshot.put("requestFlows", requestFlows().stream().map(PlatformJson::requestFlow).toList());
        snapshot.put("transactions", transactions().stream().map(PlatformJson::transaction).toList());
        snapshot.put("hosts", hosts().stream().map(PlatformJson::host).toList());
        return snapshot;
    }

    /** Returns retained request-flow samples sorted by latest start time. */
    public List<GovernanceRequestFlow> requestFlows() {
        Map<String, List<GovernanceSignal>> grouped = new LinkedHashMap<>();
        for (GovernanceSignal signal : signals()) {
            String flowKey = signal.attributes().getOrDefault("flowKey", "flow-" + signal.resourceKey().replace(':', '-'));
            grouped.computeIfAbsent(flowKey, ignored -> new ArrayList<>()).add(signal);
        }
        List<GovernanceRequestFlow> flows = new ArrayList<>();
        for (Map.Entry<String, List<GovernanceSignal>> entry : grouped.entrySet()) {
            List<GovernanceSignal> groupedSignals = entry.getValue();
            GovernanceSignal primary = groupedSignals.stream().max(this::comparePrimarySignal).orElse(null);
            if (primary == null) {
                continue;
            }
            List<GovernanceSpan> spans = new ArrayList<>();
            List<GovernanceEvidenceRef> refs = new ArrayList<>();
            int index = 0;
            for (GovernanceSignal signal : groupedSignals.stream().sorted(Comparator.comparing(GovernanceSignal::timestamp)).toList()) {
                index++;
                refs.addAll(evidenceRefs(signal));
                spans.add(span(entry.getKey(), signal, index));
            }
            long durationMs = groupedSignals.stream()
                    .mapToLong(signal -> readLong(signal.attributes(), "durationMs", fallbackFlowDuration(signal)))
                    .max()
                    .orElse(fallbackFlowDuration(primary));
            String endpoint = primary.attributes().getOrDefault("endpoint", primary.resourceKey());
            String status = primary.severity() == GovernanceSignalSeverity.CRITICAL
                    ? "ERROR"
                    : primary.severity() == GovernanceSignalSeverity.WARNING ? "SLOW" : "OK";
            flows.add(new GovernanceRequestFlow(
                    entry.getKey(),
                    primary.serviceKey(),
                    endpoint,
                    primary.zoneKey(),
                    status,
                    durationMs,
                    groupedSignals.stream().map(GovernanceSignal::timestamp).min(Comparator.naturalOrder()).orElse(primary.timestamp()),
                    spans.size(),
                    primaryError(primary),
                    primary.attributes().getOrDefault("summary", evidenceMessage(primary)),
                    spans,
                    refs.stream().distinct().toList()));
        }
        flows.sort(Comparator.comparing(GovernanceRequestFlow::startedAt).reversed());
        return flows;
    }

    /** Returns retained request-flow samples filtered and sorted for read-only trace queries. */
    public List<GovernanceRequestFlow> requestFlows(
            Instant from,
            Instant to,
            String serviceKey,
            String endpointKey,
            String status,
            Long minDurationMs,
            String resourceKey,
            String source,
            String sort) {
        return requestFlows().stream()
                .filter(flow -> matchesTraceRange(flow, from, to))
                .filter(flow -> matchesTraceService(flow, serviceKey))
                .filter(flow -> matchesTraceEndpoint(flow, endpointKey))
                .filter(flow -> matchesTraceStatus(flow, status))
                .filter(flow -> minDurationMs == null || flow.durationMs() >= minDurationMs)
                .filter(flow -> matchesTraceResource(flow, resourceKey))
                .filter(flow -> matchesTraceSource(flow, source))
                .sorted(traceComparator(sort))
                .toList();
    }

    /** Returns one request-flow sample by sanitized key. */
    public Optional<GovernanceRequestFlow> requestFlow(String traceKey) {
        if (traceKey == null || traceKey.isBlank()) {
            return Optional.empty();
        }
        return requestFlows().stream()
                .filter(flow -> flow.traceKey().equals(traceKey))
                .findFirst();
    }

    /** Returns CAT-style transaction metric projections. */
    public List<GovernanceTransactionMetric> transactions() {
        List<GovernanceTransactionMetric> items = new ArrayList<>();
        for (GovernanceSignal signal : signals()) {
            if (signal.severity() == GovernanceSignalSeverity.INFO && !signal.attributes().containsKey("metricTotal")) {
                continue;
            }
            String endpoint = signal.attributes().getOrDefault("endpoint", signal.resourceKey());
            long total = readLong(signal.attributes(), "metricTotal", signal.severity() == GovernanceSignalSeverity.CRITICAL ? 96 : 42);
            long failure = readLong(signal.attributes(), "metricFailure", signal.severity() == GovernanceSignalSeverity.CRITICAL ? 18 : 3);
            double failureRate = total == 0 ? 0.0 : (double) failure / total;
            long avgMs = readLong(signal.attributes(), "metricAvgMs", fallbackFlowDuration(signal) / 2);
            long p95Ms = readLong(signal.attributes(), "metricP95Ms", fallbackFlowDuration(signal));
            items.add(new GovernanceTransactionMetric(
                    signal.serviceKey(),
                    endpoint,
                    signal.zoneKey(),
                    signal.timestamp().minusSeconds(300),
                    signal.timestamp(),
                    total,
                    failure,
                    readDouble(signal.attributes(), "metricFailureRate", failureRate),
                    readDouble(signal.attributes(), "metricTps", total / 300.0),
                    readDouble(signal.attributes(), "metricQps", total / 300.0),
                    readLong(signal.attributes(), "metricMinMs", Math.max(1, avgMs / 4)),
                    readLong(signal.attributes(), "metricMaxMs", Math.max(p95Ms, avgMs)),
                    avgMs,
                    p95Ms,
                    readLong(signal.attributes(), "metricP99Ms", Math.max(p95Ms, fallbackFlowDuration(signal))),
                    signal.attributes().getOrDefault("flowKey", "")));
        }
        if (items.isEmpty()) {
            for (GovernanceServiceNode service : services()) {
                Map<String, String> attributes = service.attributes();
                long total = readLong(attributes, "transactionTotal", Math.max(1, Math.round(readDouble(attributes, "qps", 1.0) * 60)));
                long failure = readLong(attributes, "transactionFailure", Math.round(total * readDouble(attributes, "errorRate", 0.0)));
                items.add(new GovernanceTransactionMetric(
                        service.serviceKey(),
                        "http:" + service.serviceKey() + ":default",
                        service.zoneKey(),
                        Instant.now().minusSeconds(300),
                        Instant.now(),
                        total,
                        failure,
                        total == 0 ? 0.0 : (double) failure / total,
                        total / 300.0,
                        total / 300.0,
                        1,
                        readLong(attributes, "p99Ms", 100),
                        readLong(attributes, "p95Ms", 50),
                        readLong(attributes, "p95Ms", 50),
                        readLong(attributes, "p99Ms", 100),
                        ""));
            }
        }
        items.sort(Comparator.comparingLong(GovernanceTransactionMetric::failure).reversed()
                .thenComparing(GovernanceTransactionMetric::serviceKey));
        return items;
    }

    /** Returns host and instance waterline projections. */
    public List<GovernanceHostSignal> hosts() {
        Map<String, GovernanceAsset> assets = mergeAssets(repository.resourceReports());
        List<GovernanceHostSignal> hosts = assets.values().stream()
                .filter(asset -> asset.kind() == GovernanceAssetKind.INSTANCE)
                .map(this::hostFromAsset)
                .toList();
        if (!hosts.isEmpty()) {
            return hosts;
        }
        return services().stream()
                .map(service -> new GovernanceHostSignal(
                        service.serviceKey() + "-instance-01",
                        service.serviceKey(),
                        service.clusterKey(),
                        service.zoneKey(),
                        stateFromCounts(service.criticalCount(), service.warningCount()),
                        readDouble(service.attributes(), "cpuPercent", fallbackPercent(service)),
                        readDouble(service.attributes(), "memoryPercent", fallbackPercent(service)),
                        readDouble(service.attributes(), "swapPercent", service.criticalCount() > 0 ? 38.0 : 4.0),
                        readDouble(service.attributes(), "diskIoPercent", service.criticalCount() > 0 ? 82.0 : 22.0),
                        readDouble(service.attributes(), "networkJitterMs", 0.0),
                        readDouble(service.attributes(), "packetLossPercent", 0.0),
                        readLong(service.attributes(), "connectionCount", 240),
                        readLong(service.attributes(), "jvmThreadCount", 96),
                        readLong(service.attributes(), "gcPauseMs", 18),
                        service.criticalCount() > 0 ? "DEPENDENCY_ERROR" : "NONE",
                        Instant.now(),
                        service.attributes()))
                .toList();
    }

    private String dataSourceMode(
            List<GovernancePlatformResourceReport> reports,
            List<GovernanceSignal> retainedSignals,
            List<GovernanceConnectorStatus> connectorStatuses,
            Instant generatedAt) {
        if (reports.isEmpty() && retainedSignals.isEmpty() && connectorStatuses.isEmpty()) {
            return "UNAVAILABLE";
        }
        if (hasDemoEvidence(reports, retainedSignals, connectorStatuses)) {
            return "DEMO";
        }
        Instant latestSignalAt = latestSignalAt(retainedSignals);
        if (latestSignalAt != null && latestSignalAt.plus(STALE_AFTER).isBefore(generatedAt)) {
            return "STALE";
        }
        return "LIVE";
    }

    private Map<String, Object> dataFreshness(
            List<GovernanceSignal> retainedSignals,
            List<GovernanceConnectorStatus> connectorStatuses,
            Instant generatedAt,
            String sourceMode) {
        Map<String, Object> freshness = new LinkedHashMap<>();
        freshness.put("state", sourceMode);
        freshness.put("generatedAt", generatedAt);
        freshness.put("lastSignalAt", latestSignalAt(retainedSignals));
        freshness.put("lastConnectorSeenAt", connectorStatuses.stream()
                .map(GovernanceConnectorStatus::lastSeenAt)
                .max(Comparator.naturalOrder())
                .orElse(null));
        freshness.put("staleAfterSeconds", STALE_AFTER.toSeconds());
        return freshness;
    }

    private List<Map<String, Object>> dataSources(
            List<GovernanceConnectorStatus> connectorStatuses,
            List<GovernanceSignal> retainedSignals,
            String sourceMode) {
        if (connectorStatuses.isEmpty()) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("sourceKey", "platform-ingest");
            source.put("kind", "NEXARY_SDK");
            source.put("state", retainedSignals.isEmpty() ? "UNAVAILABLE" : sourceMode);
            source.put("displayName", "Nexary platform ingest");
            source.put("lastSeenAt", latestSignalAt(retainedSignals));
            source.put("mode", sourceMode);
            return List.of(source);
        }
        return connectorStatuses.stream()
                .map(connector -> {
                    Map<String, Object> source = new LinkedHashMap<>();
                    source.put("sourceKey", connector.connectorKey());
                    source.put("kind", connector.kind().name());
                    source.put("state", connector.state().name());
                    source.put("displayName", connector.displayName());
                    source.put("lastSeenAt", connector.lastSeenAt());
                    source.put("lastMessage", connector.lastMessage());
                    source.put("mode", sourceMode);
                    return source;
                })
                .toList();
    }

    private List<String> dataWarnings(
            String sourceMode,
            List<GovernanceConnectorStatus> connectorStatuses,
            List<GovernanceSignal> retainedSignals) {
        List<String> warnings = new ArrayList<>();
        if ("DEMO".equals(sourceMode)) {
            warnings.add("DEMO data is for sample and UI verification only.");
        }
        if ("STALE".equals(sourceMode)) {
            warnings.add("Latest signal is older than " + STALE_AFTER.toMinutes() + " minutes.");
        }
        if ("UNAVAILABLE".equals(sourceMode)) {
            warnings.add("No platform resources, signals, or connector data are available.");
        }
        connectorStatuses.stream()
                .filter(connector -> connector.state().name().equals("DEGRADED") || connector.state().name().equals("FAILED"))
                .map(connector -> connector.connectorKey() + " is " + connector.state().name())
                .forEach(warnings::add);
        if (retainedSignals.isEmpty() && !"UNAVAILABLE".equals(sourceMode)) {
            warnings.add("No retained governance signals are available for trace or incident views.");
        }
        return warnings;
    }

    private Instant latestSignalAt(List<GovernanceSignal> retainedSignals) {
        return retainedSignals.stream()
                .map(GovernanceSignal::timestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean hasDemoEvidence(
            List<GovernancePlatformResourceReport> reports,
            List<GovernanceSignal> retainedSignals,
            List<GovernanceConnectorStatus> connectorStatuses) {
        boolean demoConnector = connectorStatuses.stream()
                .anyMatch(connector -> containsDemo(connector.connectorKey())
                        || containsDemo(connector.displayName())
                        || containsDemo(connector.lastMessage()));
        boolean demoSignal = retainedSignals.stream()
                .anyMatch(signal -> "demo".equalsIgnoreCase(signal.attributes().get("source")));
        boolean demoAsset = reports.stream()
                .flatMap(report -> report.assets().stream())
                .anyMatch(asset -> containsDemo(asset.key())
                        || containsDemo(asset.name())
                        || "demo".equalsIgnoreCase(asset.attributes().get("sourceMode")));
        return demoConnector || demoSignal || demoAsset;
    }

    private boolean containsDemo(String value) {
        return value != null && value.toLowerCase().contains("demo");
    }

    private boolean matchesTraceRange(GovernanceRequestFlow flow, Instant from, Instant to) {
        Instant startedAt = flow.startedAt();
        if (startedAt == null) {
            return true;
        }
        return (from == null || !startedAt.isBefore(from)) && (to == null || !startedAt.isAfter(to));
    }

    private boolean matchesTraceService(GovernanceRequestFlow flow, String serviceKey) {
        if (isBlank(serviceKey)) {
            return true;
        }
        return containsIgnoreCase(flow.entryServiceKey(), serviceKey)
                || flow.spans().stream().anyMatch(span -> containsIgnoreCase(span.serviceKey(), serviceKey));
    }

    private boolean matchesTraceEndpoint(GovernanceRequestFlow flow, String endpointKey) {
        if (isBlank(endpointKey)) {
            return true;
        }
        return containsIgnoreCase(flow.endpointKey(), endpointKey)
                || flow.spans().stream().anyMatch(span -> containsIgnoreCase(span.operation(), endpointKey));
    }

    private boolean matchesTraceStatus(GovernanceRequestFlow flow, String status) {
        return isBlank(status) || flow.status().equalsIgnoreCase(status);
    }

    private boolean matchesTraceResource(GovernanceRequestFlow flow, String resourceKey) {
        if (isBlank(resourceKey)) {
            return true;
        }
        return containsIgnoreCase(flow.endpointKey(), resourceKey)
                || flow.spans().stream().anyMatch(span -> containsIgnoreCase(span.resourceKey(), resourceKey));
    }

    private boolean matchesTraceSource(GovernanceRequestFlow flow, String source) {
        if (isBlank(source)) {
            return true;
        }
        return flow.evidenceRefs().stream()
                .anyMatch(ref -> containsIgnoreCase(ref.type().name(), source)
                        || containsIgnoreCase(ref.label(), source)
                        || containsIgnoreCase(ref.refKey(), source));
    }

    private Comparator<GovernanceRequestFlow> traceComparator(String sort) {
        String normalized = sort == null ? "risk" : sort.trim().toLowerCase();
        return switch (normalized) {
            case "duration", "duration_desc" -> Comparator.comparingLong(GovernanceRequestFlow::durationMs).reversed();
            case "duration_asc" -> Comparator.comparingLong(GovernanceRequestFlow::durationMs);
            case "started_at", "started_at_desc", "time", "time_desc" ->
                    Comparator.comparing(GovernanceRequestFlow::startedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
            case "status" -> Comparator.comparingInt((GovernanceRequestFlow flow) -> traceStatusWeight(flow.status())).reversed()
                    .thenComparing(Comparator.comparingLong(GovernanceRequestFlow::durationMs).reversed());
            default -> Comparator.comparingInt((GovernanceRequestFlow flow) -> traceStatusWeight(flow.status())).reversed()
                    .thenComparing(Comparator.comparingLong(GovernanceRequestFlow::durationMs).reversed())
                    .thenComparing(Comparator.comparing(GovernanceRequestFlow::startedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        };
    }

    private int traceStatusWeight(String status) {
        if ("ERROR".equalsIgnoreCase(status)) {
            return 3;
        }
        if ("SLOW".equalsIgnoreCase(status) || "WARNING".equalsIgnoreCase(status)) {
            return 2;
        }
        return 1;
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null && expected != null && value.toLowerCase().contains(expected.toLowerCase());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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

    private Map<String, Object> overviewSummary(
            Map<String, GovernanceAsset> assets,
            List<GovernanceServiceNode> serviceNodes,
            List<GovernanceDependencyEdge> dependencies,
            List<GovernanceConnectorStatus> connectorStatuses,
            List<IncidentCandidate> incidentCandidates,
            List<GovernanceSignal> retainedSignals) {
        long criticalIncidents = incidentCandidates.stream()
                .filter(incident -> incident.severity() == GovernanceSignalSeverity.CRITICAL)
                .count();
        long warningIncidents = incidentCandidates.stream()
                .filter(incident -> incident.severity() == GovernanceSignalSeverity.WARNING)
                .count();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("workspaceKey", firstAssetKey(assets, GovernanceAssetKind.WORKSPACE, "unknown"));
        summary.put("environmentKey", firstAssetKey(assets, GovernanceAssetKind.ENVIRONMENT, "unknown"));
        summary.put("health", criticalIncidents > 0 ? "NEEDS_ACTION" : warningIncidents > 0 ? "WATCH" : "HEALTHY");
        summary.put("criticalIncidents", criticalIncidents);
        summary.put("warningIncidents", warningIncidents);
        summary.put("serviceCount", serviceNodes.size());
        summary.put("zoneCount", serviceNodes.stream().map(GovernanceServiceNode::zoneKey).distinct().count());
        summary.put("dependencyCount", dependencies.size());
        summary.put("connectorCount", connectorStatuses.size());
        summary.put("middlewareCount", assets.values().stream().filter(asset -> asset.kind() == GovernanceAssetKind.MIDDLEWARE).count());
        summary.put("openPolicyPlans", incidentCandidates.size());
        summary.put("notificationRoutes", connectorStatuses.stream()
                .filter(connector -> connector.kind().name().equals("FEISHU") || connector.kind().name().equals("DINGTALK"))
                .count());
        summary.put("lastSignalAt", retainedSignals.stream()
                .map(GovernanceSignal::timestamp)
                .max(Comparator.naturalOrder())
                .orElse(null));
        return summary;
    }

    private List<Map<String, Object>> serviceWatermarks(List<GovernanceServiceNode> services, List<IncidentCandidate> incidents) {
        Map<String, Long> incidentCountByService = incidents.stream()
                .collect(Collectors.groupingBy(incident -> incident.impactScope().serviceKey(), Collectors.counting()));
        return services.stream()
                .map(service -> {
                    Map<String, String> attributes = service.attributes();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("serviceKey", service.serviceKey());
                    item.put("name", service.name());
                    item.put("teamKey", service.teamKey());
                    item.put("environmentKey", service.environmentKey());
                    item.put("clusterKey", service.clusterKey());
                    item.put("zoneKey", service.zoneKey());
                    item.put("qps", readDouble(attributes, "qps", 0.0));
                    item.put("errorRate", readDouble(attributes, "errorRate", fallbackErrorRate(service)));
                    item.put("p95Ms", readLong(attributes, "p95Ms", fallbackLatency(service)));
                    item.put("p99Ms", readLong(attributes, "p99Ms", fallbackLatency(service) * 2));
                    item.put("instanceCount", readLong(attributes, "instances", 1));
                    item.put("cpuPercent", readDouble(attributes, "cpuPercent", fallbackPercent(service)));
                    item.put("memoryPercent", readDouble(attributes, "memoryPercent", fallbackPercent(service)));
                    item.put("watermarkPercent", readDouble(attributes, "watermarkPercent", fallbackPercent(service)));
                    item.put("sentinelState", attributes.getOrDefault("sentinelState", service.criticalCount() > 0 ? "OPEN" : "CLOSED"));
                    item.put("gatewayState", attributes.getOrDefault("gatewayState", service.warningCount() > 0 ? "WATCH" : "HEALTHY"));
                    item.put("warningCount", service.warningCount());
                    item.put("criticalCount", service.criticalCount());
                    item.put("activeIncidents", incidentCountByService.getOrDefault(service.serviceKey(), 0L));
                    item.put("state", stateFromCounts(service.criticalCount(), service.warningCount()));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> zoneWatermarks(List<GovernanceServiceNode> services) {
        return services.stream()
                .collect(Collectors.groupingBy(GovernanceServiceNode::zoneKey, LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<GovernanceServiceNode> zoneServices = entry.getValue();
                    long warningCount = zoneServices.stream().mapToLong(GovernanceServiceNode::warningCount).sum();
                    long criticalCount = zoneServices.stream().mapToLong(GovernanceServiceNode::criticalCount).sum();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("zoneKey", entry.getKey());
                    item.put("environmentKey", zoneServices.stream().findFirst().map(GovernanceServiceNode::environmentKey).orElse("unknown"));
                    item.put("serviceCount", zoneServices.size());
                    item.put("warningCount", warningCount);
                    item.put("criticalCount", criticalCount);
                    item.put("cpuPercent", averageAttribute(zoneServices, "cpuPercent"));
                    item.put("memoryPercent", averageAttribute(zoneServices, "memoryPercent"));
                    item.put("networkJitterMs", maxAttribute(zoneServices, "networkJitterMs"));
                    item.put("packetLossPercent", maxAttribute(zoneServices, "packetLossPercent"));
                    item.put("httpFailureRate", maxAttribute(zoneServices, "httpFailureRate"));
                    item.put("state", stateFromCounts(criticalCount, warningCount));
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> middlewareWatermarks(Map<String, GovernanceAsset> assets, List<GovernanceDependencyEdge> dependencies) {
        return assets.values().stream()
                .filter(asset -> asset.kind() == GovernanceAssetKind.MIDDLEWARE)
                .map(asset -> {
                    Map<String, String> attributes = asset.attributes();
                    long warningCount = dependencies.stream()
                            .filter(dependency -> dependency.targetKey().equals(asset.key()))
                            .mapToLong(GovernanceDependencyEdge::warningCount)
                            .sum();
                    long criticalCount = dependencies.stream()
                            .filter(dependency -> dependency.targetKey().equals(asset.key()))
                            .mapToLong(GovernanceDependencyEdge::criticalCount)
                            .sum();
                    long connectedServices = dependencies.stream()
                            .filter(dependency -> dependency.targetKey().equals(asset.key()))
                            .map(GovernanceDependencyEdge::sourceKey)
                            .distinct()
                            .count();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("middlewareKey", asset.key());
                    item.put("name", asset.name());
                    item.put("kind", attributes.getOrDefault("kind", "middleware"));
                    item.put("environmentKey", attributes.getOrDefault("environment", "unknown"));
                    item.put("zoneKey", attributes.getOrDefault("zone", "unknown"));
                    item.put("usagePercent", readDouble(attributes, "usagePercent", criticalCount > 0 ? 88.0 : warningCount > 0 ? 76.0 : 52.0));
                    item.put("latencyMs", readLong(attributes, "latencyMs", criticalCount > 0 ? 210 : 24));
                    item.put("errorRate", readDouble(attributes, "errorRate", criticalCount > 0 ? 0.08 : 0.01));
                    item.put("connectedServices", connectedServices);
                    item.put("warningCount", warningCount);
                    item.put("criticalCount", criticalCount);
                    item.put("state", attributes.getOrDefault("state", stateFromCounts(criticalCount, warningCount)));
                    return item;
                })
                .sorted(Comparator.comparing(item -> item.get("middlewareKey").toString()))
                .toList();
    }

    private List<Map<String, Object>> policyPlans(List<IncidentCandidate> incidents) {
        return incidents.stream()
                .limit(6)
                .map(incident -> {
                    String signalType = incident.evidence().isEmpty() ? "RESOURCE_EVENT" : incident.evidence().get(0).signalType().name();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("planKey", "plan-" + incident.incidentKey());
                    item.put("title", "Dry-run review for " + incident.primaryResourceKey());
                    item.put("serviceKey", incident.impactScope().serviceKey());
                    item.put("resourceKey", incident.primaryResourceKey());
                    item.put("signalType", signalType);
                    item.put("state", "DRY_RUN");
                    item.put("risk", incident.severity() == GovernanceSignalSeverity.CRITICAL ? "HIGH" : "MEDIUM");
                    item.put("proposedAction", proposedAction(signalType));
                    item.put("evidenceCount", incident.evidenceCount());
                    item.put("lastSeenAt", incident.lastSeenAt());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> notificationRoutes(List<GovernancePlatformResourceReport> reports, List<IncidentCandidate> incidents) {
        Map<String, GovernanceConnector> merged = new LinkedHashMap<>();
        for (GovernancePlatformResourceReport report : reports) {
            for (GovernanceConnector connector : report.connectors()) {
                if (connector.kind().name().equals("FEISHU") || connector.kind().name().equals("DINGTALK")) {
                    merged.put(connector.connectorKey(), connector);
                }
            }
        }
        return merged.values().stream()
                .map(connector -> {
                    Map<String, String> attributes = connector.attributes();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("routeKey", connector.connectorKey());
                    item.put("channel", connector.kind().name());
                    item.put("displayName", connector.displayName());
                    item.put("targetTeam", attributes.getOrDefault("targetTeam", "platform-team"));
                    item.put("minSeverity", attributes.getOrDefault("minSeverity", "CRITICAL"));
                    item.put("state", connector.state().name());
                    item.put("dryRun", Boolean.parseBoolean(attributes.getOrDefault("dryRun", "true")));
                    item.put("lastMessage", connector.lastMessage());
                    item.put("boundIncidentCount", incidents.stream()
                            .filter(incident -> incident.severity().name().equals(attributes.getOrDefault("minSeverity", "CRITICAL")))
                            .count());
                    return item;
                })
                .toList();
    }

    private String firstAssetKey(Map<String, GovernanceAsset> assets, GovernanceAssetKind kind, String fallback) {
        return assets.values().stream()
                .filter(asset -> asset.kind() == kind)
                .map(GovernanceAsset::key)
                .findFirst()
                .orElse(fallback);
    }

    private double averageAttribute(List<GovernanceServiceNode> services, String key) {
        return services.stream()
                .mapToDouble(service -> readDouble(service.attributes(), key, fallbackPercent(service)))
                .average()
                .orElse(0.0);
    }

    private double maxAttribute(List<GovernanceServiceNode> services, String key) {
        return services.stream()
                .mapToDouble(service -> readDouble(service.attributes(), key, 0.0))
                .max()
                .orElse(0.0);
    }

    private String stateFromCounts(long criticalCount, long warningCount) {
        if (criticalCount > 0) {
            return "CRITICAL";
        }
        if (warningCount > 0) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    private String proposedAction(String signalType) {
        return switch (signalType) {
            case "QUARANTINE_CANDIDATE", "INSTANCE_SUSPECT" -> "Keep manual approval before traffic quarantine";
            case "SENTINEL_BLOCK" -> "Review Sentinel threshold before publishing a policy";
            case "GATEWAY_DISCONNECT" -> "Check gateway route health and connection pool";
            case "RETRY_STOPPED" -> "Review retry stop reason and downstream recovery";
            case "ERROR_RATE" -> "Check recent errors before changing limits";
            case "LATENCY" -> "Check slow dependency and watermarks first";
            default -> "Review evidence before enabling a policy";
        };
    }

    private double fallbackErrorRate(GovernanceServiceNode service) {
        if (service.criticalCount() > 0) {
            return 0.09;
        }
        if (service.warningCount() > 0) {
            return 0.025;
        }
        return 0.003;
    }

    private long fallbackLatency(GovernanceServiceNode service) {
        if (service.criticalCount() > 0) {
            return 1800;
        }
        if (service.warningCount() > 0) {
            return 620;
        }
        return 120;
    }

    private double fallbackPercent(GovernanceServiceNode service) {
        if (service.criticalCount() > 0) {
            return 88.0;
        }
        if (service.warningCount() > 0) {
            return 72.0;
        }
        return 42.0;
    }

    private double readDouble(Map<String, String> attributes, String key, double fallback) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long readLong(Map<String, String> attributes, String key, long fallback) {
        String value = attributes.get(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
        String explicitKey = signal.attributes().get("incidentKey");
        if (explicitKey != null && !explicitKey.isBlank()) {
            return explicitKey;
        }
        return signal.environmentKey() + ":" + signal.serviceKey() + ":" + signal.clusterKey() + ":" + signal.zoneKey();
    }

    private String incidentTitle(GovernanceSignalSeverity severity, GovernanceSignal signal) {
        return signal.attributes().getOrDefault("incidentTitle", severity.name() + " signals on " + signal.serviceKey());
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
            case REDIS_TIMEOUT -> "Check Redis host swap, disk IO, and connection wait before changing policy";
            case REDIS_PIPELINE_ERROR -> "Check Redis pipeline error samples and client command path";
            case BROKEN_PIPE -> "Check board service reachability and downstream connection abort evidence";
            case DEPENDENCY_TIMEOUT -> "Check downstream dependency timeout and connector evidence";
            case NETWORK_JITTER, PACKET_LOSS -> "Check cross-zone network path before changing traffic policy";
            case HOST_WATERMARK -> "Check host waterline and instance saturation";
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
            case LATENCY, ERROR_RATE, ACTIVE_REQUESTS, REQUEST_RATE, NETWORK_JITTER, PACKET_LOSS, HOST_WATERMARK -> "METRIC_QUERY";
            case REDIS_TIMEOUT, REDIS_PIPELINE_ERROR, DEPENDENCY_TIMEOUT -> "DEPENDENCY_EVIDENCE";
            case BROKEN_PIPE -> "LOG_QUERY";
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
            case REDIS_TIMEOUT -> 88;
            case BROKEN_PIPE -> 86;
            case REDIS_PIPELINE_ERROR -> 84;
            case DEPENDENCY_TIMEOUT -> 82;
            case GATEWAY_DISCONNECT -> 80;
            case SENTINEL_BLOCK -> 75;
            case RETRY_STOPPED -> 70;
            case CANCELLATION -> 65;
            case ERROR_RATE -> 60;
            case LATENCY -> 55;
            case HOST_WATERMARK -> 54;
            case PACKET_LOSS -> 53;
            case NETWORK_JITTER -> 52;
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

    private GovernanceSpan span(String flowKey, GovernanceSignal signal, int index) {
        String target = signal.attributes().getOrDefault("targetService", signal.serviceKey());
        String resource = signal.attributes().getOrDefault("targetResource", signal.resourceKey());
        String component = signal.attributes().getOrDefault("component", component(signal));
        String operation = signal.attributes().getOrDefault("operation", signal.resourceKey());
        return new GovernanceSpan(
                flowKey + "-span-" + index,
                index == 1 ? "" : flowKey + "-span-1",
                target,
                resource,
                component,
                operation,
                readLong(signal.attributes(), "spanOffsetMs", Math.max(0, (index - 1) * 18L)),
                readLong(signal.attributes(), "spanDurationMs", fallbackFlowDuration(signal)),
                signal.severity() == GovernanceSignalSeverity.CRITICAL ? "ERROR" : signal.severity() == GovernanceSignalSeverity.WARNING ? "SLOW" : "OK",
                primaryError(signal),
                evidenceRefs(signal));
    }

    private String component(GovernanceSignal signal) {
        return switch (signal.signalType()) {
            case REDIS_TIMEOUT, REDIS_PIPELINE_ERROR -> "redis";
            case BROKEN_PIPE -> "http";
            case SENTINEL_BLOCK -> "sentinel";
            case GATEWAY_DISCONNECT -> "gateway";
            case NETWORK_JITTER, PACKET_LOSS -> "network";
            case HOST_WATERMARK, INSTANCE_SUSPECT, QUARANTINE_CANDIDATE -> "instance";
            case RETRY_STOPPED -> "retry";
            case ERROR_RATE, LATENCY, DEPENDENCY_TIMEOUT, ACTIVE_REQUESTS, REQUEST_RATE, CANCELLATION, RESOURCE_EVENT -> "service";
        };
    }

    private String primaryError(GovernanceSignal signal) {
        return switch (signal.signalType()) {
            case REDIS_TIMEOUT -> "REDIS_TIMEOUT";
            case REDIS_PIPELINE_ERROR -> "REDIS_PIPELINE_ERROR";
            case BROKEN_PIPE -> "BROKEN_PIPE";
            case DEPENDENCY_TIMEOUT -> "DEPENDENCY_TIMEOUT";
            case NETWORK_JITTER -> "NETWORK_JITTER";
            case PACKET_LOSS -> "PACKET_LOSS";
            case HOST_WATERMARK -> "HOST_WATERMARK";
            case SENTINEL_BLOCK -> "SENTINEL_BLOCK";
            case GATEWAY_DISCONNECT -> "GATEWAY_DISCONNECT";
            case QUARANTINE_CANDIDATE, INSTANCE_SUSPECT -> "INSTANCE_SUSPECT";
            default -> signal.outcome();
        };
    }

    private long fallbackFlowDuration(GovernanceSignal signal) {
        return switch (signal.signalType()) {
            case REDIS_TIMEOUT, DEPENDENCY_TIMEOUT -> 2400;
            case BROKEN_PIPE -> 920;
            case REDIS_PIPELINE_ERROR -> 780;
            case NETWORK_JITTER, PACKET_LOSS -> 1350;
            case HOST_WATERMARK -> 1100;
            case SENTINEL_BLOCK -> 20;
            default -> signal.severity() == GovernanceSignalSeverity.CRITICAL ? 1800 : 420;
        };
    }

    private List<GovernanceEvidenceRef> evidenceRefs(GovernanceSignal signal) {
        List<GovernanceEvidenceRef> refs = new ArrayList<>();
        evidenceRef(signal, refs, "skywalkingRef", GovernanceEvidenceRefType.SKYWALKING_TRACE, "SkyWalking trace");
        evidenceRef(signal, refs, "catRef", GovernanceEvidenceRefType.CAT_TRANSACTION, "CAT transaction");
        evidenceRef(signal, refs, "promqlRef", GovernanceEvidenceRefType.PROMQL, "PromQL");
        evidenceRef(signal, refs, "logRef", GovernanceEvidenceRefType.LOG_QUERY, "Log query");
        evidenceRef(signal, refs, "sentinelRef", GovernanceEvidenceRefType.SENTINEL_RESOURCE, "Sentinel resource");
        evidenceRef(signal, refs, "gatewayRef", GovernanceEvidenceRefType.GATEWAY_ROUTE, "Gateway route");
        evidenceRef(signal, refs, "sbaRef", GovernanceEvidenceRefType.SBA_INSTANCE, "Spring Boot Admin instance");
        evidenceRef(signal, refs, "faultTraceRef", GovernanceEvidenceRefType.NEXARY_FAULT_TRACE, "Nexary fault trace");
        if (refs.isEmpty()) {
            refs.add(new GovernanceEvidenceRef(GovernanceEvidenceRefType.LOG_QUERY,
                    signal.resourceKey().replace(':', '-'), "Platform evidence", ""));
        }
        return refs;
    }

    private void evidenceRef(GovernanceSignal signal, List<GovernanceEvidenceRef> refs, String attributeKey,
                             GovernanceEvidenceRefType type, String label) {
        String refKey = signal.attributes().get(attributeKey);
        if (refKey == null || refKey.isBlank()) {
            return;
        }
        refs.add(new GovernanceEvidenceRef(type, refKey, label, signal.attributes().getOrDefault(attributeKey + "Href", "")));
    }

    private GovernanceHostSignal hostFromAsset(GovernanceAsset asset) {
        Map<String, String> attributes = asset.attributes();
        return new GovernanceHostSignal(
                asset.key(),
                attributes.getOrDefault("service", "unknown-service"),
                attributes.getOrDefault("cluster", "unknown-cluster"),
                attributes.getOrDefault("zone", "unknown-zone"),
                attributes.getOrDefault("state", "HEALTHY"),
                readDouble(attributes, "cpuPercent", 0.0),
                readDouble(attributes, "memoryPercent", 0.0),
                readDouble(attributes, "swapPercent", 0.0),
                readDouble(attributes, "diskIoPercent", 0.0),
                readDouble(attributes, "networkJitterMs", 0.0),
                readDouble(attributes, "packetLossPercent", 0.0),
                readLong(attributes, "connectionCount", 0),
                readLong(attributes, "jvmThreadCount", 0),
                readLong(attributes, "gcPauseMs", 0),
                attributes.getOrDefault("lastError", "NONE"),
                Instant.now(),
                attributes);
    }
}
