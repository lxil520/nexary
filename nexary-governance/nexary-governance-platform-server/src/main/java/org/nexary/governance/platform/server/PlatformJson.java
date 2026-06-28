package org.nexary.governance.platform.server;

import org.nexary.governance.platform.EvidenceItem;
import org.nexary.governance.platform.GovernanceConnectorStatus;
import org.nexary.governance.platform.GovernanceDependencyEdge;
import org.nexary.governance.platform.GovernanceEvidenceRef;
import org.nexary.governance.platform.GovernanceHostSignal;
import org.nexary.governance.platform.GovernanceRequestFlow;
import org.nexary.governance.platform.GovernanceServiceNode;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSpan;
import org.nexary.governance.platform.GovernanceTopology;
import org.nexary.governance.platform.GovernanceTransactionMetric;
import org.nexary.governance.platform.ImpactScope;
import org.nexary.governance.platform.IncidentCandidate;
import org.nexary.governance.platform.SuggestedCheck;

import java.util.LinkedHashMap;
import java.util.Map;

final class PlatformJson {
    private PlatformJson() {
    }

    static Map<String, Object> topology(GovernanceTopology topology) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("services", topology.services().stream().map(PlatformJson::service).toList());
        json.put("dependencies", topology.dependencies().stream().map(PlatformJson::dependency).toList());
        json.put("connectors", topology.connectors().stream().map(PlatformJson::connector).toList());
        return json;
    }

    static Map<String, Object> service(GovernanceServiceNode service) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("serviceKey", service.serviceKey());
        json.put("name", service.name());
        json.put("teamKey", service.teamKey());
        json.put("environmentKey", service.environmentKey());
        json.put("clusterKey", service.clusterKey());
        json.put("zoneKey", service.zoneKey());
        json.put("warningCount", service.warningCount());
        json.put("criticalCount", service.criticalCount());
        json.put("attributes", service.attributes());
        return json;
    }

    static Map<String, Object> dependency(GovernanceDependencyEdge dependency) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sourceKey", dependency.sourceKey());
        json.put("targetKey", dependency.targetKey());
        json.put("kind", dependency.kind().name());
        json.put("resourceKey", dependency.resourceKey());
        json.put("warningCount", dependency.warningCount());
        json.put("criticalCount", dependency.criticalCount());
        json.put("attributes", dependency.attributes());
        return json;
    }

    static Map<String, Object> connector(GovernanceConnectorStatus connector) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("connectorKey", connector.connectorKey());
        json.put("kind", connector.kind().name());
        json.put("state", connector.state().name());
        json.put("displayName", connector.displayName());
        json.put("lastMessage", connector.lastMessage());
        json.put("lastSeenAt", connector.lastSeenAt());
        return json;
    }

    static Map<String, Object> incident(IncidentCandidate incident) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("incidentKey", incident.incidentKey());
        json.put("title", incident.title());
        json.put("severity", incident.severity().name());
        json.put("impactScope", impact(incident.impactScope()));
        json.put("evidence", incident.evidence().stream().map(PlatformJson::evidence).toList());
        json.put("suggestedCheck", suggestedCheck(incident.suggestedCheck()));
        json.put("startedAt", incident.startedAt());
        json.put("lastSeenAt", incident.lastSeenAt());
        json.put("primaryResourceKey", incident.primaryResourceKey());
        json.put("evidenceCount", incident.evidenceCount());
        json.put("impactedResourceCount", incident.impactedResourceCount());
        return json;
    }

    static Map<String, Object> signal(GovernanceSignal signal) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("workspaceKey", signal.workspaceKey());
        json.put("environmentKey", signal.environmentKey());
        json.put("serviceKey", signal.serviceKey());
        json.put("clusterKey", signal.clusterKey());
        json.put("zoneKey", signal.zoneKey());
        json.put("resourceKey", signal.resourceKey());
        json.put("signalType", signal.signalType().name());
        json.put("severity", signal.severity().name());
        json.put("outcome", signal.outcome());
        json.put("durationBucket", signal.durationBucket());
        json.put("timestamp", signal.timestamp());
        json.put("attributes", signal.attributes());
        return json;
    }

    static Map<String, Object> requestFlow(GovernanceRequestFlow flow) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("traceKey", flow.traceKey());
        json.put("entryServiceKey", flow.entryServiceKey());
        json.put("endpointKey", flow.endpointKey());
        json.put("zoneKey", flow.zoneKey());
        json.put("status", flow.status());
        json.put("durationMs", flow.durationMs());
        json.put("startedAt", flow.startedAt());
        json.put("spanCount", flow.spanCount());
        json.put("primaryError", flow.primaryError());
        json.put("summary", flow.summary());
        json.put("spans", flow.spans().stream().map(PlatformJson::span).toList());
        json.put("evidenceRefs", flow.evidenceRefs().stream().map(PlatformJson::evidenceRef).toList());
        return json;
    }

    static Map<String, Object> transaction(GovernanceTransactionMetric metric) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("serviceKey", metric.serviceKey());
        json.put("endpointKey", metric.endpointKey());
        json.put("zoneKey", metric.zoneKey());
        json.put("windowStart", metric.windowStart());
        json.put("windowEnd", metric.windowEnd());
        json.put("total", metric.total());
        json.put("failure", metric.failure());
        json.put("failureRate", metric.failureRate());
        json.put("tps", metric.tps());
        json.put("qps", metric.qps());
        json.put("minMs", metric.minMs());
        json.put("maxMs", metric.maxMs());
        json.put("avgMs", metric.avgMs());
        json.put("p95Ms", metric.p95Ms());
        json.put("p99Ms", metric.p99Ms());
        json.put("sampleTraceKey", metric.sampleTraceKey());
        return json;
    }

    static Map<String, Object> host(GovernanceHostSignal host) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("hostKey", host.hostKey());
        json.put("serviceKey", host.serviceKey());
        json.put("clusterKey", host.clusterKey());
        json.put("zoneKey", host.zoneKey());
        json.put("state", host.state());
        json.put("cpuPercent", host.cpuPercent());
        json.put("memoryPercent", host.memoryPercent());
        json.put("swapPercent", host.swapPercent());
        json.put("diskIoPercent", host.diskIoPercent());
        json.put("networkJitterMs", host.networkJitterMs());
        json.put("packetLossPercent", host.packetLossPercent());
        json.put("connectionCount", host.connectionCount());
        json.put("jvmThreadCount", host.jvmThreadCount());
        json.put("gcPauseMs", host.gcPauseMs());
        json.put("lastError", host.lastError());
        json.put("lastSeenAt", host.lastSeenAt());
        json.put("attributes", host.attributes());
        return json;
    }

    private static Map<String, Object> span(GovernanceSpan span) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("spanId", span.spanId());
        json.put("parentSpanId", span.parentSpanId());
        json.put("serviceKey", span.serviceKey());
        json.put("resourceKey", span.resourceKey());
        json.put("component", span.component());
        json.put("operation", span.operation());
        json.put("startOffsetMs", span.startOffsetMs());
        json.put("durationMs", span.durationMs());
        json.put("status", span.status());
        json.put("errorType", span.errorType());
        json.put("evidenceRefs", span.evidenceRefs().stream().map(PlatformJson::evidenceRef).toList());
        return json;
    }

    private static Map<String, Object> evidenceRef(GovernanceEvidenceRef ref) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", ref.type().name());
        json.put("refKey", ref.refKey());
        json.put("label", ref.label());
        json.put("href", ref.href());
        return json;
    }

    private static Map<String, Object> evidence(EvidenceItem evidence) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("signalType", evidence.signalType().name());
        json.put("severity", evidence.severity().name());
        json.put("serviceKey", evidence.serviceKey());
        json.put("clusterKey", evidence.clusterKey());
        json.put("zoneKey", evidence.zoneKey());
        json.put("resourceKey", evidence.resourceKey());
        json.put("outcome", evidence.outcome());
        json.put("durationBucket", evidence.durationBucket());
        json.put("message", evidence.message());
        json.put("referenceType", evidence.referenceType());
        json.put("referenceKey", evidence.referenceKey());
        json.put("timestamp", evidence.timestamp());
        return json;
    }

    private static Map<String, Object> impact(ImpactScope impact) {
        if (impact == null) {
            return Map.of();
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("serviceKey", impact.serviceKey());
        json.put("clusterKey", impact.clusterKey());
        json.put("zoneKey", impact.zoneKey());
        return json;
    }

    private static Map<String, Object> suggestedCheck(SuggestedCheck check) {
        if (check == null) {
            return Map.of();
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("resourceKey", check.resourceKey());
        json.put("message", check.message());
        return json;
    }
}
