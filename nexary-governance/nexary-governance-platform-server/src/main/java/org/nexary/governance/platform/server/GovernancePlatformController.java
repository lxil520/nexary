package org.nexary.governance.platform.server;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP API for read-only platform ingestion and query endpoints. */
@RestController
@RequestMapping("/api/platform")
public final class GovernancePlatformController {
    private final GovernancePlatformService service;

    /** Creates the controller. */
    public GovernancePlatformController(GovernancePlatformService service) {
        this.service = service;
    }

    /** Ingests a batch of platform resources and connector descriptors. */
    @PostMapping("/resources")
    public ResponseEntity<Map<String, Object>> resources(@RequestBody ResourceReportRequest request) {
        service.recordResources(request.toReport());
        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    /** Ingests a platform signal. */
    @PostMapping("/signals")
    public ResponseEntity<Map<String, Object>> signals(@RequestBody SignalRequest request) {
        service.recordSignal(request.toSignal());
        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    /** Returns the current platform topology. */
    @GetMapping("/topology")
    public Map<String, Object> topology() {
        return PlatformJson.topology(service.topology());
    }

    /** Returns dashboard-ready platform overview data. */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return service.overview();
    }

    /** Returns the complete read-only platform snapshot. */
    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        return service.snapshot();
    }

    /** Returns service nodes. */
    @GetMapping("/services")
    public Map<String, Object> services() {
        return Map.of("items", service.services().stream().map(PlatformJson::service).toList());
    }

    /** Returns incident candidates. */
    @GetMapping("/incidents")
    public Map<String, Object> incidents() {
        return Map.of("items", service.incidents().stream().map(PlatformJson::incident).toList());
    }

    /** Returns one incident candidate by stable key. */
    @GetMapping("/incidents/{incidentKey}")
    public ResponseEntity<Map<String, Object>> incident(@PathVariable("incidentKey") String incidentKey) {
        return service.incident(incidentKey)
                .map(PlatformJson::incident)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns local governance review plans. */
    @GetMapping("/plans")
    public Map<String, Object> plans() {
        return Map.of("items", service.plans().stream().map(PlatformJson::reviewPlan).toList());
    }

    /** Returns one local governance review plan by key. */
    @GetMapping("/plans/{planKey}")
    public ResponseEntity<Map<String, Object>> plan(@PathVariable("planKey") String planKey) {
        return service.plan(planKey)
                .map(PlatformJson::reviewPlan)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Calculates a dry-run for a local governance review plan. */
    @PostMapping("/plans/{planKey}/dry-run")
    public ResponseEntity<Map<String, Object>> dryRunPlan(@PathVariable("planKey") String planKey) {
        return service.dryRunPlan(planKey)
                .map(PlatformJson::dryRun)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Exports review material for a local governance review plan. */
    @PostMapping("/plans/{planKey}/export-review")
    public ResponseEntity<Map<String, Object>> exportReview(@PathVariable("planKey") String planKey) {
        return service.exportReview(planKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns local notification route metadata. */
    @GetMapping("/notification-routes")
    public Map<String, Object> notificationRoutes() {
        return Map.of("items", service.notificationRoutes().stream()
                .map(route -> PlatformJson.notificationRoute(route, service.boundIncidentCount(route)))
                .toList());
    }

    /** Renders a dry-run notification preview. */
    @PostMapping("/notification-routes/{routeKey}/preview")
    public ResponseEntity<Map<String, Object>> previewNotification(@PathVariable("routeKey") String routeKey) {
        return service.previewNotification(routeKey)
                .map(PlatformJson::notificationPreview)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Attempts an explicitly marked test notification when test mode is enabled. */
    @PostMapping("/notification-routes/{routeKey}/test")
    public ResponseEntity<Map<String, Object>> testNotification(@PathVariable("routeKey") String routeKey) {
        return service.testNotification(routeKey)
                .map(PlatformJson::notificationTest)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns local platform audit records. */
    @GetMapping("/audit-records")
    public Map<String, Object> auditRecords() {
        return Map.of("items", service.auditRecords().stream().map(PlatformJson::auditRecord).toList());
    }

    /** Returns request-flow samples. */
    @GetMapping("/request-flows")
    public Map<String, Object> requestFlows(
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "serviceKey", required = false) String serviceKey,
            @RequestParam(value = "endpoint", required = false) String endpoint,
            @RequestParam(value = "endpointKey", required = false) String endpointKey,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "minDurationMs", required = false) Long minDurationMs,
            @RequestParam(value = "resource", required = false) String resource,
            @RequestParam(value = "resourceKey", required = false) String resourceKey,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return requestFlowResponse(from, to, firstNonBlank(serviceKey, service), firstNonBlank(endpointKey, endpoint),
                status, minDurationMs, firstNonBlank(resourceKey, resource), source, sort, page, size);
    }

    /** Returns request-flow samples through the platform trace query contract. */
    @GetMapping("/traces")
    public Map<String, Object> traces(
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @RequestParam(value = "service", required = false) String service,
            @RequestParam(value = "serviceKey", required = false) String serviceKey,
            @RequestParam(value = "endpoint", required = false) String endpoint,
            @RequestParam(value = "endpointKey", required = false) String endpointKey,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "minDurationMs", required = false) Long minDurationMs,
            @RequestParam(value = "resource", required = false) String resource,
            @RequestParam(value = "resourceKey", required = false) String resourceKey,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return requestFlowResponse(from, to, firstNonBlank(serviceKey, service), firstNonBlank(endpointKey, endpoint),
                status, minDurationMs, firstNonBlank(resourceKey, resource), source, sort, page, size);
    }

    /** Returns one request-flow sample by stable key. */
    @GetMapping("/request-flows/{traceKey}")
    public ResponseEntity<Map<String, Object>> requestFlow(@PathVariable("traceKey") String traceKey) {
        return service.requestFlow(traceKey)
                .map(PlatformJson::requestFlow)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Returns one platform trace sample by stable key. */
    @GetMapping("/traces/{traceKey}")
    public ResponseEntity<Map<String, Object>> trace(@PathVariable("traceKey") String traceKey) {
        return requestFlow(traceKey);
    }

    /** Returns CAT-style transaction metrics. */
    @GetMapping("/transactions")
    public Map<String, Object> transactions() {
        return Map.of("items", service.transactions().stream().map(PlatformJson::transaction).toList());
    }

    /** Returns host and instance waterline signals. */
    @GetMapping("/hosts")
    public Map<String, Object> hosts() {
        return Map.of("items", service.hosts().stream().map(PlatformJson::host).toList());
    }

    /** Returns connector statuses. */
    @GetMapping("/connectors")
    public Map<String, Object> connectors() {
        return Map.of("items", service.connectors().stream().map(PlatformJson::connector).toList());
    }

    /** Returns retained signals. */
    @GetMapping("/signals")
    public Map<String, Object> retainedSignals() {
        return Map.of("items", service.signals().stream().map(PlatformJson::signal).toList());
    }

    private Map<String, Object> requestFlowResponse(
            Instant from,
            Instant to,
            String serviceKey,
            String endpointKey,
            String status,
            Long minDurationMs,
            String resourceKey,
            String source,
            String sort,
            Integer page,
            Integer size) {
        int effectivePage = page == null || page < 0 ? 0 : page;
        int effectiveSize = size == null ? 50 : Math.max(1, Math.min(size, 200));
        List<Map<String, Object>> filtered = service.requestFlows(from, to, serviceKey, endpointKey, status,
                        minDurationMs, resourceKey, source, sort)
                .stream()
                .map(PlatformJson::requestFlow)
                .toList();
        int fromIndex = Math.min(filtered.size(), effectivePage * effectiveSize);
        int toIndex = Math.min(filtered.size(), fromIndex + effectiveSize);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", filtered.subList(fromIndex, toIndex));
        response.put("page", effectivePage);
        response.put("size", effectiveSize);
        response.put("total", filtered.size());
        response.put("sort", sort == null || sort.isBlank() ? "risk" : sort);
        response.put("filters", traceFilters(from, to, serviceKey, endpointKey, status, minDurationMs, resourceKey, source));
        return response;
    }

    private Map<String, Object> traceFilters(
            Instant from,
            Instant to,
            String serviceKey,
            String endpointKey,
            String status,
            Long minDurationMs,
            String resourceKey,
            String source) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("from", from);
        filters.put("to", to);
        filters.put("serviceKey", serviceKey);
        filters.put("endpointKey", endpointKey);
        filters.put("status", status);
        filters.put("minDurationMs", minDurationMs);
        filters.put("resourceKey", resourceKey);
        filters.put("source", source);
        return filters;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

    /** Mutable HTTP DTO for a resource report. */
    public static final class ResourceReportRequest {
        private List<AssetRequest> assets = new ArrayList<>();
        private List<DependencyRequest> dependencies = new ArrayList<>();
        private List<ConnectorRequest> connectors = new ArrayList<>();

        /** Returns assets. */
        public List<AssetRequest> getAssets() { return assets; }
        /** Sets assets. */
        public void setAssets(List<AssetRequest> assets) { this.assets = assets == null ? new ArrayList<>() : assets; }
        /** Returns dependencies. */
        public List<DependencyRequest> getDependencies() { return dependencies; }
        /** Sets dependencies. */
        public void setDependencies(List<DependencyRequest> dependencies) { this.dependencies = dependencies == null ? new ArrayList<>() : dependencies; }
        /** Returns connectors. */
        public List<ConnectorRequest> getConnectors() { return connectors; }
        /** Sets connectors. */
        public void setConnectors(List<ConnectorRequest> connectors) { this.connectors = connectors == null ? new ArrayList<>() : connectors; }

        private GovernancePlatformResourceReport toReport() {
            return new GovernancePlatformResourceReport(
                    assets.stream().map(AssetRequest::toAsset).toList(),
                    dependencies.stream().map(DependencyRequest::toDependency).toList(),
                    connectors.stream().map(ConnectorRequest::toConnector).toList());
        }
    }

    /** Mutable HTTP DTO for a platform asset. */
    public static final class AssetRequest {
        private String kind;
        private String key;
        private String name;
        private Map<String, String> attributes = new LinkedHashMap<>();

        /** Returns kind. */
        public String getKind() { return kind; }
        /** Sets kind. */
        public void setKind(String kind) { this.kind = kind; }
        /** Returns key. */
        public String getKey() { return key; }
        /** Sets key. */
        public void setKey(String key) { this.key = key; }
        /** Returns name. */
        public String getName() { return name; }
        /** Sets name. */
        public void setName(String name) { this.name = name; }
        /** Returns attributes. */
        public Map<String, String> getAttributes() { return attributes; }
        /** Sets attributes. */
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes == null ? new LinkedHashMap<>() : attributes; }

        private GovernanceAsset toAsset() {
            return new GovernanceAsset(GovernanceAssetKind.valueOf(kind), key, name, attributes);
        }
    }

    /** Mutable HTTP DTO for a platform dependency. */
    public static final class DependencyRequest {
        private String sourceKey;
        private String targetKey;
        private String kind;
        private String resourceKey;
        private Map<String, String> attributes = new LinkedHashMap<>();

        /** Returns source key. */
        public String getSourceKey() { return sourceKey; }
        /** Sets source key. */
        public void setSourceKey(String sourceKey) { this.sourceKey = sourceKey; }
        /** Returns target key. */
        public String getTargetKey() { return targetKey; }
        /** Sets target key. */
        public void setTargetKey(String targetKey) { this.targetKey = targetKey; }
        /** Returns kind. */
        public String getKind() { return kind; }
        /** Sets kind. */
        public void setKind(String kind) { this.kind = kind; }
        /** Returns resource key. */
        public String getResourceKey() { return resourceKey; }
        /** Sets resource key. */
        public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
        /** Returns attributes. */
        public Map<String, String> getAttributes() { return attributes; }
        /** Sets attributes. */
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes == null ? new LinkedHashMap<>() : attributes; }

        private GovernanceDependency toDependency() {
            return new GovernanceDependency(sourceKey, targetKey, GovernanceDependencyKind.valueOf(kind), resourceKey, attributes);
        }
    }

    /** Mutable HTTP DTO for a connector descriptor. */
    public static final class ConnectorRequest {
        private String connectorKey;
        private String kind;
        private String state;
        private String displayName;
        private String lastMessage;
        private Map<String, String> attributes = new LinkedHashMap<>();

        /** Returns connector key. */
        public String getConnectorKey() { return connectorKey; }
        /** Sets connector key. */
        public void setConnectorKey(String connectorKey) { this.connectorKey = connectorKey; }
        /** Returns kind. */
        public String getKind() { return kind; }
        /** Sets kind. */
        public void setKind(String kind) { this.kind = kind; }
        /** Returns state. */
        public String getState() { return state; }
        /** Sets state. */
        public void setState(String state) { this.state = state; }
        /** Returns display name. */
        public String getDisplayName() { return displayName; }
        /** Sets display name. */
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        /** Returns last message. */
        public String getLastMessage() { return lastMessage; }
        /** Sets last message. */
        public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
        /** Returns attributes. */
        public Map<String, String> getAttributes() { return attributes; }
        /** Sets attributes. */
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes == null ? new LinkedHashMap<>() : attributes; }

        private GovernanceConnector toConnector() {
            return new GovernanceConnector(
                    connectorKey,
                    GovernanceConnectorKind.valueOf(kind),
                    GovernanceConnectorState.valueOf(state),
                    displayName,
                    lastMessage,
                    attributes);
        }
    }

    /** Mutable HTTP DTO for a platform signal. */
    public static final class SignalRequest {
        private String workspaceKey;
        private String environmentKey;
        private String serviceKey;
        private String clusterKey;
        private String zoneKey;
        private String resourceKey;
        private String signalType;
        private String severity;
        private String outcome;
        private String durationBucket;
        private Instant timestamp;
        private Map<String, String> attributes = new LinkedHashMap<>();

        /** Returns workspace key. */
        public String getWorkspaceKey() { return workspaceKey; }
        /** Sets workspace key. */
        public void setWorkspaceKey(String workspaceKey) { this.workspaceKey = workspaceKey; }
        /** Returns environment key. */
        public String getEnvironmentKey() { return environmentKey; }
        /** Sets environment key. */
        public void setEnvironmentKey(String environmentKey) { this.environmentKey = environmentKey; }
        /** Returns service key. */
        public String getServiceKey() { return serviceKey; }
        /** Sets service key. */
        public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
        /** Returns cluster key. */
        public String getClusterKey() { return clusterKey; }
        /** Sets cluster key. */
        public void setClusterKey(String clusterKey) { this.clusterKey = clusterKey; }
        /** Returns zone key. */
        public String getZoneKey() { return zoneKey; }
        /** Sets zone key. */
        public void setZoneKey(String zoneKey) { this.zoneKey = zoneKey; }
        /** Returns resource key. */
        public String getResourceKey() { return resourceKey; }
        /** Sets resource key. */
        public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
        /** Returns signal type. */
        public String getSignalType() { return signalType; }
        /** Sets signal type. */
        public void setSignalType(String signalType) { this.signalType = signalType; }
        /** Returns severity. */
        public String getSeverity() { return severity; }
        /** Sets severity. */
        public void setSeverity(String severity) { this.severity = severity; }
        /** Returns outcome. */
        public String getOutcome() { return outcome; }
        /** Sets outcome. */
        public void setOutcome(String outcome) { this.outcome = outcome; }
        /** Returns duration bucket. */
        public String getDurationBucket() { return durationBucket; }
        /** Sets duration bucket. */
        public void setDurationBucket(String durationBucket) { this.durationBucket = durationBucket; }
        /** Returns timestamp. */
        public Instant getTimestamp() { return timestamp; }
        /** Sets timestamp. */
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        /** Returns attributes. */
        public Map<String, String> getAttributes() { return attributes; }
        /** Sets attributes. */
        public void setAttributes(Map<String, String> attributes) { this.attributes = attributes == null ? new LinkedHashMap<>() : attributes; }

        private GovernanceSignal toSignal() {
            return new GovernanceSignal(
                    workspaceKey,
                    environmentKey,
                    serviceKey,
                    clusterKey,
                    zoneKey,
                    resourceKey,
                    GovernanceSignalType.valueOf(signalType),
                    GovernanceSignalSeverity.valueOf(severity),
                    outcome,
                    durationBucket,
                    timestamp,
                    attributes);
        }
    }
}
