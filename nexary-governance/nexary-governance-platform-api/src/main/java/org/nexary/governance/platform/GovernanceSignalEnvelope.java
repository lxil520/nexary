package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Map;

/** HTTP-friendly signal envelope accepted by platform ingestion endpoints. */
public final class GovernanceSignalEnvelope {
    private String workspaceKey;
    private String environmentKey;
    private String serviceKey;
    private String clusterKey;
    private String zoneKey;
    private String resourceKey;
    private GovernanceSignalType signalType;
    private GovernanceSignalSeverity severity;
    private String outcome;
    private String durationBucket;
    private Instant timestamp;
    private Map<String, String> attributes;

    /** Creates an empty envelope for JSON binding. */
    public GovernanceSignalEnvelope() {
    }

    /** Creates an envelope from a signal. */
    public GovernanceSignalEnvelope(GovernanceSignal signal) {
        this.workspaceKey = signal.workspaceKey();
        this.environmentKey = signal.environmentKey();
        this.serviceKey = signal.serviceKey();
        this.clusterKey = signal.clusterKey();
        this.zoneKey = signal.zoneKey();
        this.resourceKey = signal.resourceKey();
        this.signalType = signal.signalType();
        this.severity = signal.severity();
        this.outcome = signal.outcome();
        this.durationBucket = signal.durationBucket();
        this.timestamp = signal.timestamp();
        this.attributes = signal.attributes();
    }

    /** Converts this envelope to the immutable platform signal model. */
    public GovernanceSignal toSignal() {
        return new GovernanceSignal(
                workspaceKey,
                environmentKey,
                serviceKey,
                clusterKey,
                zoneKey,
                resourceKey,
                signalType,
                severity,
                outcome,
                durationBucket,
                timestamp,
                attributes);
    }

    public String getWorkspaceKey() { return workspaceKey; }
    public void setWorkspaceKey(String workspaceKey) { this.workspaceKey = workspaceKey; }
    public String getEnvironmentKey() { return environmentKey; }
    public void setEnvironmentKey(String environmentKey) { this.environmentKey = environmentKey; }
    public String getServiceKey() { return serviceKey; }
    public void setServiceKey(String serviceKey) { this.serviceKey = serviceKey; }
    public String getClusterKey() { return clusterKey; }
    public void setClusterKey(String clusterKey) { this.clusterKey = clusterKey; }
    public String getZoneKey() { return zoneKey; }
    public void setZoneKey(String zoneKey) { this.zoneKey = zoneKey; }
    public String getResourceKey() { return resourceKey; }
    public void setResourceKey(String resourceKey) { this.resourceKey = resourceKey; }
    public GovernanceSignalType getSignalType() { return signalType; }
    public void setSignalType(GovernanceSignalType signalType) { this.signalType = signalType; }
    public GovernanceSignalSeverity getSeverity() { return severity; }
    public void setSeverity(GovernanceSignalSeverity severity) { this.severity = severity; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getDurationBucket() { return durationBucket; }
    public void setDurationBucket(String durationBucket) { this.durationBucket = durationBucket; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
}
