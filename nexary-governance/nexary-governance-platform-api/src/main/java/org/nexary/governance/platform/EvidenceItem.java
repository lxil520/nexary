package org.nexary.governance.platform;

import java.time.Instant;

/** One low-cardinality evidence item attached to an incident candidate. */
public final class EvidenceItem {
    private final GovernanceSignalType signalType;
    private final GovernanceSignalSeverity severity;
    private final String resourceKey;
    private final String outcome;
    private final Instant timestamp;

    /** Creates an evidence item. */
    public EvidenceItem(
            GovernanceSignalType signalType,
            GovernanceSignalSeverity severity,
            String resourceKey,
            String outcome,
            Instant timestamp) {
        this.signalType = signalType == null ? GovernanceSignalType.RESOURCE_EVENT : signalType;
        this.severity = severity == null ? GovernanceSignalSeverity.INFO : severity;
        this.resourceKey = GovernancePlatformValidators.token(resourceKey, "resourceKey");
        this.outcome = GovernancePlatformValidators.token(outcome == null ? "NONE" : outcome, "outcome");
        this.timestamp = timestamp;
    }

    /** Returns the signal type. */
    public GovernanceSignalType signalType() { return signalType; }
    /** Returns the evidence severity. */
    public GovernanceSignalSeverity severity() { return severity; }
    /** Returns the resource key. */
    public String resourceKey() { return resourceKey; }
    /** Returns the fixed outcome bucket. */
    public String outcome() { return outcome; }
    /** Returns when the evidence happened. */
    public Instant timestamp() { return timestamp; }
}
