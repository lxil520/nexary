package org.nexary.governance.platform.server;

import org.nexary.governance.platform.EvidenceItem;
import org.nexary.governance.platform.GovernanceAuditAction;
import org.nexary.governance.platform.GovernanceAuditRecord;
import org.nexary.governance.platform.GovernanceAsset;
import org.nexary.governance.platform.GovernanceAssetKind;
import org.nexary.governance.platform.GovernanceConnectorAccessMode;
import org.nexary.governance.platform.GovernanceConnectorAuthMode;
import org.nexary.governance.platform.GovernanceConnectorCapability;
import org.nexary.governance.platform.GovernanceConnector;
import org.nexary.governance.platform.GovernanceConnectorConfig;
import org.nexary.governance.platform.GovernanceConnectorStatus;
import org.nexary.governance.platform.GovernanceConnectorKind;
import org.nexary.governance.platform.GovernanceConnectorState;
import org.nexary.governance.platform.GovernanceConnectorTestResult;
import org.nexary.governance.platform.GovernanceDependency;
import org.nexary.governance.platform.GovernanceDependencyEdge;
import org.nexary.governance.platform.GovernanceDryRunResult;
import org.nexary.governance.platform.GovernanceEvidenceRef;
import org.nexary.governance.platform.GovernanceEvidenceRefType;
import org.nexary.governance.platform.GovernanceHostSignal;
import org.nexary.governance.platform.GovernanceNotificationMode;
import org.nexary.governance.platform.GovernanceNotificationPreview;
import org.nexary.governance.platform.GovernanceNotificationRoute;
import org.nexary.governance.platform.GovernanceNotificationTestResult;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernancePlanDiff;
import org.nexary.governance.platform.GovernancePlanRisk;
import org.nexary.governance.platform.GovernancePlanState;
import org.nexary.governance.platform.GovernancePlanTarget;
import org.nexary.governance.platform.GovernancePlanTargetKind;
import org.nexary.governance.platform.GovernanceRequestFlow;
import org.nexary.governance.platform.GovernanceReviewPlan;
import org.nexary.governance.platform.GovernanceServiceMapping;
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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    /** Returns local connector configurations sorted by connector key. */
    public List<GovernanceConnectorConfig> connectorConfigs() {
        return repository.connectorConfigs().stream()
                .sorted(Comparator.comparing(GovernanceConnectorConfig::connectorKey))
                .toList();
    }

    /** Returns one local connector configuration by key. */
    public Optional<GovernanceConnectorConfig> connectorConfig(String connectorKey) {
        if (isBlank(connectorKey)) {
            return Optional.empty();
        }
        return repository.connectorConfig(connectorKey);
    }

    /** Stores a local connector configuration and keeps external systems unchanged. */
    public GovernanceConnectorConfig saveConnectorConfig(GovernanceConnectorConfig config) {
        GovernanceConnectorConfig sanitized = withDefaultCapabilities(Objects.requireNonNull(config, "config"));
        repository.saveConnectorConfig(sanitized);
        if (sanitized.kind() == GovernanceConnectorKind.FEISHU || sanitized.kind() == GovernanceConnectorKind.DINGTALK) {
            repository.saveNotificationRoute(routeFromConnectorConfig(sanitized));
        }
        repository.saveAuditRecord(audit(
                GovernanceAuditAction.CONNECTOR_CONFIG_SAVED,
                sanitized.connectorKey(),
                "OK",
                "Local connector configuration saved"));
        return sanitized;
    }

    /** Attempts an explicit local connector test. Production writes are never performed. */
    public Optional<GovernanceConnectorTestResult> testConnector(String connectorKey) {
        Optional<GovernanceConnectorConfig> config = connectorConfig(connectorKey);
        if (config.isEmpty()) {
            return Optional.empty();
        }
        GovernanceConnectorConfig current = config.get();
        GovernanceConnectorTestResult result = executeConnectorTest(current);
        repository.saveConnectorTestResult(result);
        repository.saveConnectorConfig(copyConfigWithState(
                current,
                result.accepted() ? GovernanceConnectorState.HEALTHY : GovernanceConnectorState.DEGRADED,
                result.message()));
        if (current.kind() == GovernanceConnectorKind.FEISHU || current.kind() == GovernanceConnectorKind.DINGTALK) {
            repository.saveNotificationRoute(routeFromConnectorConfig(copyConfigWithState(
                    current,
                    result.accepted() ? GovernanceConnectorState.HEALTHY : GovernanceConnectorState.DEGRADED,
                    result.message())));
        }
        repository.saveAuditRecord(audit(
                GovernanceAuditAction.CONNECTOR_TEST,
                current.connectorKey(),
                result.accepted() ? "OK" : result.status(),
                result.message()));
        return Optional.of(result);
    }

    /** Returns retained connector test results sorted newest first. */
    public List<GovernanceConnectorTestResult> connectorTestResults() {
        return repository.connectorTestResults().stream()
                .sorted(Comparator.comparing(GovernanceConnectorTestResult::testedAt).reversed())
                .toList();
    }

    /** Returns local service mappings sorted by service key. */
    public List<GovernanceServiceMapping> serviceMappings() {
        return repository.serviceMappings().stream()
                .sorted(Comparator.comparing(GovernanceServiceMapping::serviceKey)
                        .thenComparing(GovernanceServiceMapping::sourceKind)
                        .thenComparing(GovernanceServiceMapping::mappingKey))
                .toList();
    }

    /** Stores a local service-to-tool mapping. */
    public GovernanceServiceMapping saveServiceMapping(GovernanceServiceMapping mapping) {
        GovernanceServiceMapping current = Objects.requireNonNull(mapping, "mapping");
        repository.saveServiceMapping(current);
        repository.saveAuditRecord(audit(
                GovernanceAuditAction.SERVICE_MAPPING_SAVED,
                current.mappingKey(),
                "OK",
                "Local service mapping saved"));
        return current;
    }

    /** Returns retained signals sorted newest first. */
    public List<GovernanceSignal> signals() {
        return repository.signals().stream()
                .sorted(Comparator.comparing(GovernanceSignal::timestamp).reversed())
                .toList();
    }

    /** Returns local governance review plans generated from current incident evidence. */
    public List<GovernanceReviewPlan> plans() {
        ensureReviewPlans();
        return repository.reviewPlans().stream()
                .sorted(Comparator.comparing(GovernanceReviewPlan::updatedAt).reversed()
                        .thenComparing(GovernanceReviewPlan::planKey))
                .toList();
    }

    /** Returns one local governance review plan by key. */
    public Optional<GovernanceReviewPlan> plan(String planKey) {
        if (isBlank(planKey)) {
            return Optional.empty();
        }
        ensureReviewPlans();
        return repository.reviewPlan(planKey);
    }

    /** Calculates a dry-run result for a local governance review plan. */
    public Optional<GovernanceDryRunResult> dryRunPlan(String planKey) {
        Optional<GovernanceReviewPlan> plan = plan(planKey);
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        GovernanceReviewPlan current = plan.get();
        List<GovernanceRequestFlow> matchingFlows = requestFlows().stream()
                .filter(flow -> flow.entryServiceKey().equals(current.serviceKey())
                        || flow.spans().stream().anyMatch(span -> span.resourceKey().equals(current.resourceKey())))
                .toList();
        List<String> impactedServices = matchingFlows.stream()
                .flatMap(flow -> flow.spans().stream().map(GovernanceSpan::serviceKey))
                .distinct()
                .limit(8)
                .toList();
        if (impactedServices.isEmpty()) {
            impactedServices = List.of(current.serviceKey());
        }
        List<String> impactedInstances = hosts().stream()
                .filter(host -> host.serviceKey().equals(current.serviceKey()))
                .map(GovernanceHostSignal::hostKey)
                .distinct()
                .limit(8)
                .toList();
        List<String> impactedDependencies = topology().dependencies().stream()
                .filter(edge -> edge.sourceKey().equals(current.serviceKey()) || edge.resourceKey().equals(current.resourceKey()))
                .map(GovernanceDependencyEdge::resourceKey)
                .distinct()
                .limit(8)
                .toList();
        List<String> blockers = dryRunBlockers(current, matchingFlows);
        GovernanceDryRunResult result = new GovernanceDryRunResult(
                current.planKey(),
                blockers.isEmpty(),
                current.risk(),
                impactedServices,
                impactedInstances,
                impactedDependencies,
                matchingFlows.size(),
                blockers,
                current.diffs(),
                current.evidence(),
                "TEST / DRY-RUN only; external systems are not changed",
                Instant.now());
        repository.saveAuditRecord(audit(
                GovernanceAuditAction.PLAN_DRY_RUN,
                current.planKey(),
                result.passed() ? "OK" : "BLOCKED",
                result.summary()));
        repository.saveReviewPlan(copyPlanWithState(current, result.passed() ? GovernancePlanState.DRY_RUN : GovernancePlanState.BLOCKED));
        return Optional.of(result);
    }

    /** Exports review material for a local governance review plan. */
    public Optional<Map<String, Object>> exportReview(String planKey) {
        Optional<GovernanceReviewPlan> plan = plan(planKey);
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        GovernanceReviewPlan current = plan.get();
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("planKey", current.planKey());
        review.put("incidentKey", current.incidentKey());
        review.put("title", current.title());
        review.put("mode", "REVIEW_ONLY");
        review.put("summary", current.proposedAction());
        review.put("plan", PlatformJson.reviewPlan(current));
        repository.saveAuditRecord(audit(
                GovernanceAuditAction.PLAN_EXPORT_REVIEW,
                current.planKey(),
                "OK",
                "Review material exported"));
        repository.saveReviewPlan(copyPlanWithState(current, GovernancePlanState.REVIEW_EXPORTED));
        return Optional.of(review);
    }

    /** Returns local notification routes derived from read-only connector metadata. */
    public List<GovernanceNotificationRoute> notificationRoutes() {
        ensureNotificationRoutes();
        return repository.notificationRoutes().stream()
                .sorted(Comparator.comparing(GovernanceNotificationRoute::routeKey))
                .toList();
    }

    /** Renders a dry-run notification preview for a route. */
    public Optional<GovernanceNotificationPreview> previewNotification(String routeKey) {
        Optional<GovernanceNotificationRoute> route = notificationRoute(routeKey);
        if (route.isEmpty()) {
            return Optional.empty();
        }
        IncidentCandidate incident = incidentForRoute(route.get()).orElse(noIncident(route.get()));
        GovernanceNotificationPreview preview = notificationPreview(route.get(), incident, GovernanceNotificationMode.DRY_RUN);
        repository.saveAuditRecord(audit(
                GovernanceAuditAction.NOTIFICATION_PREVIEW,
                route.get().routeKey(),
                "OK",
                "Notification preview rendered"));
        return Optional.of(preview);
    }

    /** Attempts an explicitly marked test notification. Production send is not supported. */
    public Optional<GovernanceNotificationTestResult> testNotification(String routeKey) {
        Optional<GovernanceNotificationRoute> route = notificationRoute(routeKey);
        if (route.isEmpty()) {
            return Optional.empty();
        }
        GovernanceNotificationRoute current = route.get();
        IncidentCandidate incident = incidentForRoute(current).orElse(noIncident(current));
        GovernanceNotificationPreview preview = notificationPreview(current, incident, GovernanceNotificationMode.TEST);
        boolean enabled = current.testEnabled() && current.mode() == GovernanceNotificationMode.TEST
                && current.state() == GovernanceConnectorState.HEALTHY;
        GovernanceNotificationTestResult result = enabled
                ? sendTestNotification(current, preview)
                : notificationTestResult(
                current,
                preview,
                false,
                "TEST_DISABLED",
                "Notification test is disabled; configure test mode before sending");
        repository.saveNotificationTestResult(result);
        repository.saveAuditRecord(audit(
                GovernanceAuditAction.NOTIFICATION_TEST,
                current.routeKey(),
                notificationTestAuditResult(result),
                result.message()));
        return Optional.of(result);
    }

    /** Returns local audit records sorted newest first. */
    public List<GovernanceAuditRecord> auditRecords() {
        return repository.auditRecords().stream()
                .sorted(Comparator.comparing(GovernanceAuditRecord::createdAt).reversed())
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
        overview.put("policyPlans", plans().stream().limit(6).map(PlatformJson::reviewPlan).toList());
        overview.put("notificationRoutes", notificationRoutes().stream()
                .map(route -> PlatformJson.notificationRoute(route, boundIncidentCount(route)))
                .toList());
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
        snapshot.put("plans", plans().stream().map(PlatformJson::reviewPlan).toList());
        snapshot.put("notificationRoutes", notificationRoutes().stream()
                .map(route -> PlatformJson.notificationRoute(route, boundIncidentCount(route)))
                .toList());
        snapshot.put("connectorConfigs", connectorConfigs().stream().map(PlatformJson::connectorConfig).toList());
        snapshot.put("connectorTests", connectorTestResults().stream().limit(20).map(PlatformJson::connectorTest).toList());
        snapshot.put("serviceMappings", serviceMappings().stream().map(PlatformJson::serviceMapping).toList());
        snapshot.put("auditRecords", auditRecords().stream().limit(20).map(PlatformJson::auditRecord).toList());
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

    private void ensureReviewPlans() {
        for (IncidentCandidate incident : incidents()) {
            String planKey = "plan-" + incident.incidentKey();
            if (repository.reviewPlan(planKey).isPresent()) {
                continue;
            }
            GovernanceReviewPlan plan = reviewPlanFromIncident(incident);
            repository.saveReviewPlan(plan);
            repository.saveAuditRecord(audit(
                    GovernanceAuditAction.PLAN_GENERATED,
                    plan.planKey(),
                    "OK",
                    "Review plan generated from incident evidence"));
        }
    }

    private GovernanceReviewPlan reviewPlanFromIncident(IncidentCandidate incident) {
        String signalType = incident.evidence().isEmpty()
                ? GovernanceSignalType.RESOURCE_EVENT.name()
                : incident.evidence().get(0).signalType().name();
        GovernancePlanTarget target = new GovernancePlanTarget(
                planTargetKind(signalType),
                incident.primaryResourceKey(),
                incident.primaryResourceKey());
        List<GovernancePlanDiff> diffs = List.of(planDiff(signalType, incident));
        int impactedInstances = (int) hosts().stream()
                .filter(host -> host.serviceKey().equals(incident.impactScope().serviceKey()))
                .map(GovernanceHostSignal::hostKey)
                .distinct()
                .count();
        return new GovernanceReviewPlan(
                "plan-" + incident.incidentKey(),
                incident.incidentKey(),
                "Review " + incident.primaryResourceKey(),
                GovernancePlanState.DRAFT,
                planRisk(incident),
                target,
                diffs,
                incident.impactScope().serviceKey(),
                incident.primaryResourceKey(),
                proposedAction(signalType),
                incident.evidence(),
                incident.evidenceCount(),
                1,
                impactedInstances,
                incident.startedAt() == null ? Instant.now() : incident.startedAt(),
                incident.lastSeenAt() == null ? Instant.now() : incident.lastSeenAt());
    }

    private GovernancePlanTargetKind planTargetKind(String signalType) {
        return switch (signalType) {
            case "SENTINEL_BLOCK" -> GovernancePlanTargetKind.SENTINEL_RESOURCE;
            case "GATEWAY_DISCONNECT", "BROKEN_PIPE", "CANCELLATION" -> GovernancePlanTargetKind.GATEWAY_ROUTE;
            case "QUARANTINE_CANDIDATE", "INSTANCE_SUSPECT", "HOST_WATERMARK" -> GovernancePlanTargetKind.INSTANCE_CANDIDATE;
            case "LATENCY", "ERROR_RATE", "ACTIVE_REQUESTS", "REQUEST_RATE", "DEPENDENCY_TIMEOUT" ->
                    GovernancePlanTargetKind.ALERT_THRESHOLD;
            default -> GovernancePlanTargetKind.OWNERSHIP_MAPPING;
        };
    }

    private GovernancePlanRisk planRisk(IncidentCandidate incident) {
        if (incident.severity() == GovernanceSignalSeverity.CRITICAL && incident.evidenceCount() >= 3) {
            return GovernancePlanRisk.CRITICAL;
        }
        if (incident.severity() == GovernanceSignalSeverity.CRITICAL) {
            return GovernancePlanRisk.HIGH;
        }
        return incident.evidenceCount() > 2 ? GovernancePlanRisk.MEDIUM : GovernancePlanRisk.LOW;
    }

    private GovernancePlanDiff planDiff(String signalType, IncidentCandidate incident) {
        return switch (planTargetKind(signalType)) {
            case SENTINEL_RESOURCE -> new GovernancePlanDiff(
                    "sentinelResourcePolicy",
                    "current-policy",
                    "review-threshold-for-" + incident.primaryResourceKey(),
                    "Sentinel evidence requires human threshold review");
            case GATEWAY_ROUTE -> new GovernancePlanDiff(
                    "gatewayRoutePolicy",
                    "current-route",
                    "review-timeout-and-retry",
                    "Gateway evidence requires route review");
            case INSTANCE_CANDIDATE -> new GovernancePlanDiff(
                    "instanceIsolation",
                    "none",
                    "candidate-only",
                    "Instance signal requires manual traffic review");
            case ALERT_THRESHOLD -> new GovernancePlanDiff(
                    "alertThreshold",
                    "current-threshold",
                    "review-window-from-evidence",
                    "Metric evidence requires alert threshold review");
            case OWNERSHIP_MAPPING -> new GovernancePlanDiff(
                    "ownershipMapping",
                    "unknown-owner",
                    incident.impactScope().serviceKey(),
                    "Evidence should be mapped to service ownership");
        };
    }

    private List<String> dryRunBlockers(GovernanceReviewPlan plan, List<GovernanceRequestFlow> matchingFlows) {
        List<String> blockers = new ArrayList<>();
        if (plan.evidence().isEmpty()) {
            blockers.add("No retained evidence is attached to this plan");
        }
        if (matchingFlows.isEmpty()) {
            blockers.add("No retained request samples match this plan");
        }
        if (plan.risk() == GovernancePlanRisk.CRITICAL && plan.evidenceCount() < 2) {
            blockers.add("Critical plan requires at least two evidence items");
        }
        return blockers;
    }

    private GovernanceReviewPlan copyPlanWithState(GovernanceReviewPlan plan, GovernancePlanState state) {
        return new GovernanceReviewPlan(
                plan.planKey(),
                plan.incidentKey(),
                plan.title(),
                state,
                plan.risk(),
                plan.target(),
                plan.diffs(),
                plan.serviceKey(),
                plan.resourceKey(),
                plan.proposedAction(),
                plan.evidence(),
                plan.evidenceCount(),
                plan.impactedServiceCount(),
                plan.impactedInstanceCount(),
                plan.createdAt(),
                Instant.now());
    }

    private void ensureNotificationRoutes() {
        for (GovernancePlatformResourceReport report : repository.resourceReports()) {
            for (GovernanceConnector connector : report.connectors()) {
                if (connector.kind() != GovernanceConnectorKind.FEISHU && connector.kind() != GovernanceConnectorKind.DINGTALK) {
                    continue;
                }
                if (repository.notificationRoute(connector.connectorKey()).isPresent()) {
                    continue;
                }
                GovernanceNotificationRoute route = routeFromConnector(connector);
                repository.saveNotificationRoute(route);
            }
        }
    }

    private GovernanceNotificationRoute routeFromConnector(GovernanceConnector connector) {
        Map<String, String> attributes = connector.attributes();
        boolean testEnabled = Boolean.parseBoolean(attributes.getOrDefault("testEnabled", "false"));
        GovernanceNotificationMode mode = testEnabled
                ? GovernanceNotificationMode.TEST
                : Boolean.parseBoolean(attributes.getOrDefault("dryRun", "true"))
                ? GovernanceNotificationMode.DRY_RUN
                : GovernanceNotificationMode.DISABLED;
        Map<String, String> publicAttributes = new LinkedHashMap<>();
        publicAttributes.put("readOnly", "true");
        publicAttributes.put("writeDisabled", "true");
        publicAttributes.put("testEnabled", Boolean.toString(testEnabled));
        publicAttributes.put("productionSend", "false");
        return new GovernanceNotificationRoute(
                connector.connectorKey(),
                connector.kind().name(),
                connector.displayName(),
                attributes.getOrDefault("targetTeam", "platform-team"),
                severity(attributes.getOrDefault("minSeverity", "CRITICAL")),
                mode,
                connector.state(),
                testEnabled,
                connector.lastMessage(),
                publicAttributes);
    }

    private GovernanceNotificationRoute routeFromConnectorConfig(GovernanceConnectorConfig config) {
        GovernanceNotificationMode mode = config.testEnabled()
                ? GovernanceNotificationMode.TEST
                : config.accessMode() == GovernanceConnectorAccessMode.DISABLED
                ? GovernanceNotificationMode.DISABLED
                : GovernanceNotificationMode.DRY_RUN;
        Map<String, String> publicAttributes = new LinkedHashMap<>();
        publicAttributes.put("readOnly", "true");
        publicAttributes.put("writeDisabled", "true");
        publicAttributes.put("testEnabled", Boolean.toString(config.testEnabled()));
        publicAttributes.put("productionSend", "false");
        publicAttributes.put("source", "connector-config");
        return new GovernanceNotificationRoute(
                config.connectorKey(),
                config.kind().name(),
                config.displayName(),
                config.attributes().getOrDefault("targetTeam", "platform-team"),
                severity(config.attributes().getOrDefault("minSeverity", "CRITICAL")),
                mode,
                config.state(),
                config.testEnabled(),
                config.lastMessage(),
                publicAttributes);
    }

    private GovernanceConnectorConfig withDefaultCapabilities(GovernanceConnectorConfig config) {
        return new GovernanceConnectorConfig(
                config.connectorKey(),
                config.kind(),
                config.displayName(),
                config.endpoint(),
                config.authMode(),
                config.accessMode(),
                config.state(),
                config.testEnabled(),
                capabilitiesFor(config.kind(), config.accessMode(), config.testEnabled()),
                config.lastMessage(),
                config.attributes(),
                config.createdAt(),
                Instant.now());
    }

    private GovernanceConnectorConfig copyConfigWithState(
            GovernanceConnectorConfig config,
            GovernanceConnectorState state,
            String lastMessage) {
        return new GovernanceConnectorConfig(
                config.connectorKey(),
                config.kind(),
                config.displayName(),
                config.endpoint(),
                config.authMode(),
                config.accessMode(),
                state,
                config.testEnabled(),
                config.capabilities(),
                lastMessage,
                config.attributes(),
                config.createdAt(),
                Instant.now());
    }

    private List<GovernanceConnectorCapability> capabilitiesFor(
            GovernanceConnectorKind kind,
            GovernanceConnectorAccessMode accessMode,
            boolean testEnabled) {
        List<GovernanceConnectorCapability> capabilities = new ArrayList<>();
        switch (kind) {
            case SKYWALKING -> {
                capabilities.add(GovernanceConnectorCapability.READ_TOPOLOGY);
                capabilities.add(GovernanceConnectorCapability.READ_TRACES);
                capabilities.add(GovernanceConnectorCapability.READ_ALERTS);
            }
            case PROMETHEUS, MICROMETER -> capabilities.add(GovernanceConnectorCapability.READ_METRICS);
            case GATEWAY -> {
                capabilities.add(GovernanceConnectorCapability.READ_GATEWAY_ROUTES);
                capabilities.add(GovernanceConnectorCapability.DRY_RUN_PLAN);
            }
            case SENTINEL -> {
                capabilities.add(GovernanceConnectorCapability.READ_SENTINEL_STATE);
                capabilities.add(GovernanceConnectorCapability.DRY_RUN_PLAN);
            }
            case ALERTMANAGER -> capabilities.add(GovernanceConnectorCapability.READ_ALERTS);
            case FEISHU, DINGTALK -> {
                if (testEnabled) {
                    capabilities.add(GovernanceConnectorCapability.TEST_NOTIFICATION);
                }
            }
            default -> capabilities.add(GovernanceConnectorCapability.READ_METRICS);
        }
        if (accessMode == GovernanceConnectorAccessMode.DRY_RUN) {
            capabilities.add(GovernanceConnectorCapability.DRY_RUN_PLAN);
        }
        capabilities.add(GovernanceConnectorCapability.WRITE_DISABLED);
        return capabilities.stream().distinct().toList();
    }

    private GovernanceConnectorTestResult executeConnectorTest(GovernanceConnectorConfig config) {
        if (config.accessMode() == GovernanceConnectorAccessMode.DISABLED) {
            return connectorTestResult(config, false, "DISABLED", "Connector is disabled locally");
        }
        if (isBlank(config.endpoint())) {
            return connectorTestResult(config, false, "TEST_CONFIG_MISSING", "Connector endpoint is not configured");
        }
        if ((config.kind() == GovernanceConnectorKind.FEISHU || config.kind() == GovernanceConnectorKind.DINGTALK)
                && !config.testEnabled()) {
            return connectorTestResult(config, false, "TEST_DISABLED", "Notification connector test is disabled");
        }
        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.endpoint()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json");
            if (config.kind() == GovernanceConnectorKind.FEISHU || config.kind() == GovernanceConnectorKind.DINGTALK) {
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(testConnectorPayload(config)));
            } else {
                builder.GET();
            }
            request = builder.build();
        } catch (IllegalArgumentException exception) {
            return connectorTestResult(config, false, "TEST_CONFIG_INVALID", "Connector endpoint is invalid");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 500;
            String status = accepted ? "TEST_REACHABLE" : "TEST_FAILED";
            if (config.kind() == GovernanceConnectorKind.FEISHU || config.kind() == GovernanceConnectorKind.DINGTALK) {
                accepted = response.statusCode() >= 200 && response.statusCode() < 300;
                status = accepted ? "TEST_SENT" : "TEST_FAILED";
            }
            return connectorTestResult(
                    config,
                    accepted,
                    status,
                    accepted ? "Connector test completed; production writes disabled" : "Connector endpoint returned HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return connectorTestResult(config, false, "TEST_FAILED", "Connector test was interrupted");
        } catch (IOException exception) {
            return connectorTestResult(config, false, "TEST_FAILED", "Connector test request failed");
        }
    }

    private GovernanceConnectorTestResult connectorTestResult(
            GovernanceConnectorConfig config,
            boolean accepted,
            String status,
            String message) {
        return new GovernanceConnectorTestResult(
                "test-" + config.connectorKey() + "-" + Instant.now().toEpochMilli(),
                config.connectorKey(),
                accepted,
                status,
                message,
                Instant.now(),
                config.capabilities());
    }

    private String testConnectorPayload(GovernanceConnectorConfig config) {
        return "{\"msg_type\":\"text\",\"content\":{\"text\":\"TEST / DRY-RUN Nexary connector test: "
                + jsonEscape(config.displayName()) + "\"}}";
    }

    private GovernanceNotificationTestResult sendTestNotification(
            GovernanceNotificationRoute route,
            GovernanceNotificationPreview preview) {
        Optional<GovernanceConnector> connector = connectorForRoute(route.routeKey());
        String webhookUrl = connector.map(value -> value.attributes().getOrDefault("webhookUrl", "")).orElse("");
        if (isBlank(webhookUrl)) {
            return notificationTestResult(
                    route,
                    preview,
                    false,
                    "TEST_CONFIG_MISSING",
                    "Notification test webhook is not configured");
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(testNotificationPayload(preview)))
                    .build();
        } catch (IllegalArgumentException exception) {
            return notificationTestResult(route, preview, false, "TEST_CONFIG_INVALID", "Notification test webhook is invalid");
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            boolean accepted = response.statusCode() >= 200 && response.statusCode() < 300;
            return notificationTestResult(
                    route,
                    preview,
                    accepted,
                    accepted ? "TEST_SENT" : "TEST_FAILED",
                    accepted ? "TEST / DRY-RUN message sent" : "Notification test webhook returned HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return notificationTestResult(route, preview, false, "TEST_FAILED", "Notification test webhook was interrupted");
        } catch (IOException exception) {
            return notificationTestResult(route, preview, false, "TEST_FAILED", "Notification test webhook request failed");
        }
    }

    private GovernanceNotificationTestResult notificationTestResult(
            GovernanceNotificationRoute route,
            GovernanceNotificationPreview preview,
            boolean accepted,
            String status,
            String message) {
        return new GovernanceNotificationTestResult(
                "test-" + route.routeKey() + "-" + Instant.now().toEpochMilli(),
                route.routeKey(),
                accepted,
                status,
                message,
                Instant.now(),
                preview);
    }

    private Optional<GovernanceConnector> connectorForRoute(String routeKey) {
        return repository.resourceReports().stream()
                .flatMap(report -> report.connectors().stream())
                .filter(connector -> connector.connectorKey().equals(routeKey))
                .findFirst();
    }

    private String testNotificationPayload(GovernanceNotificationPreview preview) {
        return "{"
                + "\"mode\":\"TEST\","
                + "\"subject\":\"" + jsonEscape(preview.subject()) + "\","
                + "\"body\":\"" + jsonEscape(preview.body()) + "\","
                + "\"routeKey\":\"" + jsonEscape(preview.routeKey()) + "\","
                + "\"incidentKey\":\"" + jsonEscape(preview.incidentKey()) + "\""
                + "}";
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String notificationTestAuditResult(GovernanceNotificationTestResult result) {
        if (result.accepted()) {
            return "OK";
        }
        return "TEST_DISABLED".equals(result.status()) ? "DISABLED" : "FAILED";
    }

    private Optional<GovernanceNotificationRoute> notificationRoute(String routeKey) {
        if (isBlank(routeKey)) {
            return Optional.empty();
        }
        ensureNotificationRoutes();
        return repository.notificationRoute(routeKey);
    }

    private Optional<IncidentCandidate> incidentForRoute(GovernanceNotificationRoute route) {
        return incidents().stream()
                .filter(incident -> severityWeight(incident.severity()) >= severityWeight(route.minSeverity()))
                .findFirst();
    }

    private IncidentCandidate noIncident(GovernanceNotificationRoute route) {
        Instant now = Instant.now();
        return new IncidentCandidate(
                "incident-none-" + route.routeKey(),
                "No active incident for " + route.displayName(),
                GovernanceSignalSeverity.INFO,
                new ImpactScope("unknown", "unknown", "unknown"),
                List.of(),
                new SuggestedCheck(route.routeKey(), "No production notification will be sent"),
                now,
                now,
                route.routeKey(),
                0,
                0);
    }

    private GovernanceNotificationPreview notificationPreview(
            GovernanceNotificationRoute route,
            IncidentCandidate incident,
            GovernanceNotificationMode mode) {
        String subject = "TEST / DRY-RUN " + incident.severity().name() + " " + incident.primaryResourceKey();
        String body = "TEST / DRY-RUN only; review evidence before action";
        return new GovernanceNotificationPreview(
                route.routeKey(),
                incident.incidentKey(),
                subject,
                body,
                List.of(route.targetTeam()),
                mode,
                Instant.now());
    }

    /** Counts active incident candidates bound to a local notification route. */
    public long boundIncidentCount(GovernanceNotificationRoute route) {
        return incidents().stream()
                .filter(incident -> severityWeight(incident.severity()) >= severityWeight(route.minSeverity()))
                .count();
    }

    private GovernanceSignalSeverity severity(String value) {
        if (isBlank(value)) {
            return GovernanceSignalSeverity.CRITICAL;
        }
        try {
            return GovernanceSignalSeverity.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return GovernanceSignalSeverity.CRITICAL;
        }
    }

    private GovernanceAuditRecord audit(
            GovernanceAuditAction action,
            String subjectKey,
            String result,
            String message) {
        Instant now = Instant.now();
        String suffix = Integer.toUnsignedString(Objects.hash(action, subjectKey, now), 36);
        return new GovernanceAuditRecord(
                "audit-" + action.name().toLowerCase() + "-" + now.toEpochMilli() + "-" + suffix,
                action,
                subjectKey,
                result,
                message,
                now);
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
        for (GovernanceConnectorConfig config : repository.connectorConfigs()) {
            merged.putIfAbsent(config.connectorKey(), new GovernanceConnector(
                    config.connectorKey(),
                    config.kind(),
                    config.state(),
                    config.displayName(),
                    config.lastMessage(),
                    config.attributes()));
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
