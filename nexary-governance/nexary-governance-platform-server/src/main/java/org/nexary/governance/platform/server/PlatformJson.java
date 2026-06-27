package org.nexary.governance.platform.server;

import org.nexary.governance.platform.EvidenceItem;
import org.nexary.governance.platform.GovernanceConnectorStatus;
import org.nexary.governance.platform.GovernanceDependencyEdge;
import org.nexary.governance.platform.GovernanceServiceNode;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceTopology;
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
        json.put("lastSeenAt", incident.lastSeenAt());
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

    private static Map<String, Object> evidence(EvidenceItem evidence) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("signalType", evidence.signalType().name());
        json.put("severity", evidence.severity().name());
        json.put("resourceKey", evidence.resourceKey());
        json.put("outcome", evidence.outcome());
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
