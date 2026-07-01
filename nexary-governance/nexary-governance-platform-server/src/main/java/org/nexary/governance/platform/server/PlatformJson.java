package org.nexary.governance.platform.server;

import org.nexary.governance.platform.EvidenceItem;
import org.nexary.governance.platform.GovernanceAuditRecord;
import org.nexary.governance.platform.GovernanceConnectorStatus;
import org.nexary.governance.platform.GovernanceDependencyEdge;
import org.nexary.governance.platform.GovernanceDryRunResult;
import org.nexary.governance.platform.GovernanceEvidenceRef;
import org.nexary.governance.platform.GovernanceHostSignal;
import org.nexary.governance.platform.GovernanceNotificationPreview;
import org.nexary.governance.platform.GovernanceNotificationRoute;
import org.nexary.governance.platform.GovernanceNotificationTestResult;
import org.nexary.governance.platform.GovernancePlanDiff;
import org.nexary.governance.platform.GovernancePlanTarget;
import org.nexary.governance.platform.GovernanceRequestFlow;
import org.nexary.governance.platform.GovernanceReviewPlan;
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

    static Map<String, Object> reviewPlan(GovernanceReviewPlan plan) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("planKey", plan.planKey());
        json.put("incidentKey", plan.incidentKey());
        json.put("title", plan.title());
        json.put("state", plan.state().name());
        json.put("risk", plan.risk().name());
        json.put("target", planTarget(plan.target()));
        json.put("diffs", plan.diffs().stream().map(PlatformJson::planDiff).toList());
        json.put("serviceKey", plan.serviceKey());
        json.put("resourceKey", plan.resourceKey());
        json.put("proposedAction", plan.proposedAction());
        json.put("evidence", plan.evidence().stream().map(PlatformJson::evidence).toList());
        json.put("evidenceCount", plan.evidenceCount());
        json.put("impactedServiceCount", plan.impactedServiceCount());
        json.put("impactedInstanceCount", plan.impactedInstanceCount());
        json.put("createdAt", plan.createdAt());
        json.put("updatedAt", plan.updatedAt());
        return json;
    }

    static Map<String, Object> dryRun(GovernanceDryRunResult result) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("planKey", result.planKey());
        json.put("passed", result.passed());
        json.put("risk", result.risk().name());
        json.put("impactedServices", result.impactedServices());
        json.put("impactedInstances", result.impactedInstances());
        json.put("impactedDependencies", result.impactedDependencies());
        json.put("requestSampleCount", result.requestSampleCount());
        json.put("blockers", result.blockers());
        json.put("diffs", result.diffs().stream().map(PlatformJson::planDiff).toList());
        json.put("evidence", result.evidence().stream().map(PlatformJson::evidence).toList());
        json.put("summary", result.summary());
        json.put("generatedAt", result.generatedAt());
        return json;
    }

    static Map<String, Object> notificationRoute(GovernanceNotificationRoute route, long boundIncidentCount) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("routeKey", route.routeKey());
        json.put("channel", route.channel());
        json.put("displayName", route.displayName());
        json.put("targetTeam", route.targetTeam());
        json.put("minSeverity", route.minSeverity().name());
        json.put("mode", route.mode().name());
        json.put("state", route.state().name());
        json.put("dryRun", route.mode().name().equals("DRY_RUN"));
        json.put("testEnabled", route.testEnabled());
        json.put("lastMessage", route.lastMessage());
        json.put("boundIncidentCount", boundIncidentCount);
        json.put("attributes", route.attributes());
        return json;
    }

    static Map<String, Object> notificationPreview(GovernanceNotificationPreview preview) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("routeKey", preview.routeKey());
        json.put("incidentKey", preview.incidentKey());
        json.put("subject", preview.subject());
        json.put("body", preview.body());
        json.put("recipients", preview.recipients());
        json.put("mode", preview.mode().name());
        json.put("createdAt", preview.createdAt());
        return json;
    }

    static Map<String, Object> notificationTest(GovernanceNotificationTestResult result) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("testKey", result.testKey());
        json.put("routeKey", result.routeKey());
        json.put("accepted", result.accepted());
        json.put("status", result.status());
        json.put("message", result.message());
        json.put("attemptedAt", result.attemptedAt());
        json.put("preview", result.preview() == null ? Map.of() : notificationPreview(result.preview()));
        return json;
    }

    static Map<String, Object> auditRecord(GovernanceAuditRecord record) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("auditKey", record.auditKey());
        json.put("action", record.action().name());
        json.put("subjectKey", record.subjectKey());
        json.put("result", record.result());
        json.put("message", record.message());
        json.put("createdAt", record.createdAt());
        return json;
    }

    private static Map<String, Object> planTarget(GovernancePlanTarget target) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("kind", target.kind().name());
        json.put("targetKey", target.targetKey());
        json.put("displayName", target.displayName());
        return json;
    }

    private static Map<String, Object> planDiff(GovernancePlanDiff diff) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("fieldKey", diff.fieldKey());
        json.put("beforeValue", diff.beforeValue());
        json.put("afterValue", diff.afterValue());
        json.put("reason", diff.reason());
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
