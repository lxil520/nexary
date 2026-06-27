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
    private final Instant lastSeenAt;

    /** Creates an incident candidate. */
    public IncidentCandidate(
            String incidentKey,
            String title,
            GovernanceSignalSeverity severity,
            ImpactScope impactScope,
            List<EvidenceItem> evidence,
            SuggestedCheck suggestedCheck,
            Instant lastSeenAt) {
        this.incidentKey = GovernancePlatformValidators.token(incidentKey, "incidentKey");
        this.title = GovernancePlatformValidators.label(title, "title");
        this.severity = severity == null ? GovernanceSignalSeverity.WARNING : severity;
        this.impactScope = impactScope;
        this.evidence = immutableList(evidence);
        this.suggestedCheck = suggestedCheck;
        this.lastSeenAt = lastSeenAt;
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
    /** Returns when the candidate was last seen. */
    public Instant lastSeenAt() { return lastSeenAt; }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
