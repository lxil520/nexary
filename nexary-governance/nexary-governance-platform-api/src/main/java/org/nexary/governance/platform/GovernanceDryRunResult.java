package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Dry-run calculation result for a governance review plan. */
public final class GovernanceDryRunResult {
    private final String planKey;
    private final boolean passed;
    private final GovernancePlanRisk risk;
    private final List<String> impactedServices;
    private final List<String> impactedInstances;
    private final List<String> impactedDependencies;
    private final long requestSampleCount;
    private final List<String> blockers;
    private final List<GovernancePlanDiff> diffs;
    private final List<EvidenceItem> evidence;
    private final String summary;
    private final Instant generatedAt;

    /** Creates a dry-run result. */
    public GovernanceDryRunResult(
            String planKey,
            boolean passed,
            GovernancePlanRisk risk,
            List<String> impactedServices,
            List<String> impactedInstances,
            List<String> impactedDependencies,
            long requestSampleCount,
            List<String> blockers,
            List<GovernancePlanDiff> diffs,
            List<EvidenceItem> evidence,
            String summary,
            Instant generatedAt) {
        this.planKey = GovernancePlatformValidators.token(planKey, "planKey");
        this.passed = passed;
        this.risk = risk == null ? GovernancePlanRisk.MEDIUM : risk;
        this.impactedServices = tokens(impactedServices, "impactedServices");
        this.impactedInstances = tokens(impactedInstances, "impactedInstances");
        this.impactedDependencies = tokens(impactedDependencies, "impactedDependencies");
        this.requestSampleCount = Math.max(0L, requestSampleCount);
        this.blockers = labels(blockers, "blockers");
        this.diffs = diffs == null ? Collections.emptyList() : List.copyOf(diffs);
        this.evidence = evidence == null ? Collections.emptyList() : List.copyOf(evidence);
        this.summary = GovernancePlatformValidators.label(summary == null ? "Dry-run calculated" : summary, "summary");
        this.generatedAt = generatedAt == null ? Instant.now() : generatedAt;
    }

    /** Returns the stable plan key. */
    public String planKey() { return planKey; }
    /** Returns whether no blockers were found. */
    public boolean passed() { return passed; }
    /** Returns the calculated risk. */
    public GovernancePlanRisk risk() { return risk; }
    /** Returns impacted services. */
    public List<String> impactedServices() { return impactedServices; }
    /** Returns impacted instances. */
    public List<String> impactedInstances() { return impactedInstances; }
    /** Returns impacted dependency resource keys. */
    public List<String> impactedDependencies() { return impactedDependencies; }
    /** Returns retained request sample count. */
    public long requestSampleCount() { return requestSampleCount; }
    /** Returns blocking reasons. */
    public List<String> blockers() { return blockers; }
    /** Returns suggested diffs. */
    public List<GovernancePlanDiff> diffs() { return diffs; }
    /** Returns evidence used by the dry-run. */
    public List<EvidenceItem> evidence() { return evidence; }
    /** Returns a short review summary. */
    public String summary() { return summary; }
    /** Returns generation time. */
    public Instant generatedAt() { return generatedAt; }

    private static List<String> tokens(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().map(value -> GovernancePlatformValidators.token(value, field)).toList();
    }

    private static List<String> labels(List<String> values, String field) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().map(value -> GovernancePlatformValidators.label(value, field)).toList();
    }
}
