package org.nexary.governance.platform;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Sanitized request-flow sample that joins trace, log, metric, and governance evidence.
 *
 * @param traceKey sanitized platform trace key
 * @param entryServiceKey entry service key
 * @param endpointKey endpoint or route key
 * @param zoneKey zone or room key
 * @param status fixed request status bucket
 * @param durationMs total request duration in milliseconds
 * @param startedAt request start time
 * @param spanCount retained span count
 * @param primaryError fixed primary error bucket
 * @param summary operator-facing summary
 * @param spans retained sanitized spans
 * @param evidenceRefs external evidence references
 */
public record GovernanceRequestFlow(
        String traceKey,
        String entryServiceKey,
        String endpointKey,
        String zoneKey,
        String status,
        long durationMs,
        Instant startedAt,
        int spanCount,
        String primaryError,
        String summary,
        List<GovernanceSpan> spans,
        List<GovernanceEvidenceRef> evidenceRefs) {

    /** Creates a sanitized request-flow sample. */
    public GovernanceRequestFlow {
        traceKey = GovernancePlatformValidators.token(traceKey, "traceKey");
        entryServiceKey = GovernancePlatformValidators.token(entryServiceKey, "entryServiceKey");
        endpointKey = GovernancePlatformValidators.token(endpointKey, "endpointKey");
        zoneKey = GovernancePlatformValidators.token(zoneKey, "zoneKey");
        status = GovernancePlatformValidators.token(status == null ? "OK" : status, "status");
        durationMs = Math.max(0, durationMs);
        startedAt = startedAt == null ? Instant.now() : startedAt;
        spanCount = Math.max(0, spanCount);
        primaryError = GovernancePlatformValidators.token(primaryError == null ? "NONE" : primaryError, "primaryError");
        summary = GovernancePlatformValidators.label(summary == null ? primaryError : summary, "summary");
        spans = List.copyOf(Objects.requireNonNullElse(spans, List.of()));
        evidenceRefs = List.copyOf(Objects.requireNonNullElse(evidenceRefs, List.of()));
    }
}
