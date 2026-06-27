package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Low-cardinality signal ingested by the read-only governance platform. */
public final class GovernanceSignal {
    private final String workspaceKey;
    private final String environmentKey;
    private final String serviceKey;
    private final String clusterKey;
    private final String zoneKey;
    private final String resourceKey;
    private final GovernanceSignalType signalType;
    private final GovernanceSignalSeverity severity;
    private final String outcome;
    private final String durationBucket;
    private final Instant timestamp;
    private final Map<String, String> attributes;

    /** Creates a platform signal. */
    public GovernanceSignal(
            String workspaceKey,
            String environmentKey,
            String serviceKey,
            String clusterKey,
            String zoneKey,
            String resourceKey,
            GovernanceSignalType signalType,
            GovernanceSignalSeverity severity,
            String outcome,
            String durationBucket,
            Instant timestamp,
            Map<String, String> attributes) {
        this.workspaceKey = GovernancePlatformValidators.token(workspaceKey, "workspaceKey");
        this.environmentKey = GovernancePlatformValidators.token(environmentKey, "environmentKey");
        this.serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        this.clusterKey = GovernancePlatformValidators.token(clusterKey, "clusterKey");
        this.zoneKey = GovernancePlatformValidators.token(zoneKey, "zoneKey");
        this.resourceKey = GovernancePlatformValidators.token(resourceKey, "resourceKey");
        this.signalType = Objects.requireNonNull(signalType, "signalType");
        this.severity = severity == null ? GovernanceSignalSeverity.INFO : severity;
        this.outcome = GovernancePlatformValidators.token(outcome == null ? "NONE" : outcome, "outcome");
        this.durationBucket = GovernancePlatformValidators.token(durationBucket == null ? "NOT_RUN" : durationBucket, "durationBucket");
        this.timestamp = timestamp == null ? Instant.now() : timestamp;
        this.attributes = GovernancePlatformValidators.attributes(attributes);
    }

    /** Returns the workspace key. */
    public String workspaceKey() {
        return workspaceKey;
    }

    /** Returns the environment key. */
    public String environmentKey() {
        return environmentKey;
    }

    /** Returns the service key. */
    public String serviceKey() {
        return serviceKey;
    }

    /** Returns the cluster key. */
    public String clusterKey() {
        return clusterKey;
    }

    /** Returns the zone key. */
    public String zoneKey() {
        return zoneKey;
    }

    /** Returns the mapped resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the fixed signal type. */
    public GovernanceSignalType signalType() {
        return signalType;
    }

    /** Returns the fixed signal severity. */
    public GovernanceSignalSeverity severity() {
        return severity;
    }

    /** Returns the fixed outcome bucket. */
    public String outcome() {
        return outcome;
    }

    /** Returns the fixed duration bucket. */
    public String durationBucket() {
        return durationBucket;
    }

    /** Returns when the signal happened. */
    public Instant timestamp() {
        return timestamp;
    }

    /** Returns bounded low-cardinality attributes. */
    public Map<String, String> attributes() {
        return attributes;
    }
}
