package org.nexary.governance.platform;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only incident candidate derived from platform signals. */
public final class IncidentCandidate {
    private final String incidentKey;
    private final String title;
    private final GovernanceSignalSeverity severity;
    private final ImpactScope impactScope;
    private final List<EvidenceItem> evidence;
    private final SuggestedCheck suggestedCheck;
    private final Instant startedAt;
    private final Instant lastSeenAt;
    private final String primaryResourceKey;
    private final int evidenceCount;
    private final int impactedResourceCount;

    /** Creates an incident candidate. */
    public IncidentCandidate(
            String incidentKey,
            String title,
            GovernanceSignalSeverity severity,
            ImpactScope impactScope,
            List<EvidenceItem> evidence,
            SuggestedCheck suggestedCheck,
            Instant lastSeenAt) {
        this(
                incidentKey,
                title,
                severity,
                impactScope,
                evidence,
                suggestedCheck,
                null,
                lastSeenAt,
                suggestedCheck == null ? "unknown" : suggestedCheck.resourceKey(),
                evidence == null ? 0 : evidence.size(),
                evidence == null ? 0 : (int) evidence.stream().map(EvidenceItem::resourceKey).distinct().count());
    }

    /** Creates an incident candidate with summary and impact metadata. */
    public IncidentCandidate(
            String incidentKey,
            String title,
            GovernanceSignalSeverity severity,
            ImpactScope impactScope,
            List<EvidenceItem> evidence,
            SuggestedCheck suggestedCheck,
            Instant startedAt,
            Instant lastSeenAt,
            String primaryResourceKey,
            int evidenceCount,
            int impactedResourceCount) {
        this.incidentKey = GovernancePlatformValidators.token(incidentKey, "incidentKey");
        this.title = GovernancePlatformValidators.label(title, "title");
        this.severity = severity == null ? GovernanceSignalSeverity.WARNING : severity;
        this.impactScope = impactScope;
        this.evidence = immutableList(evidence);
        this.suggestedCheck = suggestedCheck;
        this.startedAt = startedAt;
        this.lastSeenAt = lastSeenAt;
        this.primaryResourceKey = GovernancePlatformValidators.token(primaryResourceKey == null ? "unknown" : primaryResourceKey, "primaryResourceKey");
        this.evidenceCount = Math.max(0, evidenceCount);
        this.impactedResourceCount = Math.max(0, impactedResourceCount);
    }

    /** Returns the incident candidate key. */
    public String incidentKey() { return incidentKey; }
    /** Returns the incident title. */
    public String title() { return title; }
    /** Returns the highest severity. */
    public GovernanceSignalSeverity severity() { return severity; }
    /** Returns the impacted scope. */
    public ImpactScope impactScope() { return impactScope; }
    /** Returns retained evidence items. */
    public List<EvidenceItem> evidence() { return evidence; }
    /** Returns the suggested read-only next check. */
    public SuggestedCheck suggestedCheck() { return suggestedCheck; }
    /** Returns the first retained signal timestamp in the incident window. */
    public Instant startedAt() { return startedAt; }
    /** Returns when the candidate was last seen. */
    public Instant lastSeenAt() { return lastSeenAt; }
    /** Returns the resource that should be inspected first. */
    public String primaryResourceKey() { return primaryResourceKey; }
    /** Returns the total evidence count before UI truncation. */
    public int evidenceCount() { return evidenceCount; }
    /** Returns the number of distinct impacted resources. */
    public int impactedResourceCount() { return impactedResourceCount; }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
