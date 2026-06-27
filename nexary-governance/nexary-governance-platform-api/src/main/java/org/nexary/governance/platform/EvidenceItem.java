package org.nexary.governance.platform;

import java.time.Instant;

/** One low-cardinality evidence item attached to an incident candidate. */
public final class EvidenceItem {
    private final GovernanceSignalType signalType;
    private final GovernanceSignalSeverity severity;
    private final String serviceKey;
    private final String clusterKey;
    private final String zoneKey;
    private final String resourceKey;
    private final String outcome;
    private final String durationBucket;
    private final String message;
    private final String referenceType;
    private final String referenceKey;
    private final Instant timestamp;

    /** Creates an evidence item. */
    public EvidenceItem(
            GovernanceSignalType signalType,
            GovernanceSignalSeverity severity,
            String resourceKey,
            String outcome,
            Instant timestamp) {
        this(
                signalType,
                severity,
                "unknown",
                "unknown",
                "unknown",
                resourceKey,
                outcome,
                "NOT_RUN",
                "Signal retained as incident evidence",
                "NONE",
                "NONE",
                timestamp);
    }

    /** Creates an evidence item with platform scope and read-only jump metadata. */
    public EvidenceItem(
            GovernanceSignalType signalType,
            GovernanceSignalSeverity severity,
            String serviceKey,
            String clusterKey,
            String zoneKey,
            String resourceKey,
            String outcome,
            String durationBucket,
            String message,
            String referenceType,
            String referenceKey,
            Instant timestamp) {
        this.signalType = signalType == null ? GovernanceSignalType.RESOURCE_EVENT : signalType;
        this.severity = severity == null ? GovernanceSignalSeverity.INFO : severity;
        this.serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        this.clusterKey = GovernancePlatformValidators.token(clusterKey, "clusterKey");
        this.zoneKey = GovernancePlatformValidators.token(zoneKey, "zoneKey");
        this.resourceKey = GovernancePlatformValidators.token(resourceKey, "resourceKey");
        this.outcome = GovernancePlatformValidators.token(outcome == null ? "NONE" : outcome, "outcome");
        this.durationBucket = GovernancePlatformValidators.token(durationBucket == null ? "NOT_RUN" : durationBucket, "durationBucket");
        this.message = GovernancePlatformValidators.label(message == null ? "Signal retained as incident evidence" : message, "message");
        this.referenceType = GovernancePlatformValidators.token(referenceType == null ? "NONE" : referenceType, "referenceType");
        this.referenceKey = GovernancePlatformValidators.token(referenceKey == null ? "NONE" : referenceKey, "referenceKey");
        this.timestamp = timestamp;
    }

    /** Returns the signal type. */
    public GovernanceSignalType signalType() { return signalType; }
    /** Returns the evidence severity. */
    public GovernanceSignalSeverity severity() { return severity; }
    /** Returns the impacted service key. */
    public String serviceKey() { return serviceKey; }
    /** Returns the impacted cluster key. */
    public String clusterKey() { return clusterKey; }
    /** Returns the impacted zone key. */
    public String zoneKey() { return zoneKey; }
    /** Returns the resource key. */
    public String resourceKey() { return resourceKey; }
    /** Returns the fixed outcome bucket. */
    public String outcome() { return outcome; }
    /** Returns the fixed duration bucket. */
    public String durationBucket() { return durationBucket; }
    /** Returns a short low-cardinality evidence message. */
    public String message() { return message; }
    /** Returns the reference type for a read-only external or local drill-down. */
    public String referenceType() { return referenceType; }
    /** Returns the low-cardinality reference key for a read-only drill-down. */
    public String referenceKey() { return referenceKey; }
    /** Returns when the evidence happened. */
    public Instant timestamp() { return timestamp; }
}
