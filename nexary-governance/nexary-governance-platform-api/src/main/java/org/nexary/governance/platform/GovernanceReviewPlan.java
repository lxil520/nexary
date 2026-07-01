package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Local, read-only governance plan generated from incidents and evidence. */
public final class GovernanceReviewPlan {
    private final String planKey;
    private final String incidentKey;
    private final String title;
    private final GovernancePlanState state;
    private final GovernancePlanRisk risk;
    private final GovernancePlanTarget target;
    private final List<GovernancePlanDiff> diffs;
    private final String serviceKey;
    private final String resourceKey;
    private final String proposedAction;
    private final List<EvidenceItem> evidence;
    private final int evidenceCount;
    private final int impactedServiceCount;
    private final int impactedInstanceCount;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** Creates a local governance review plan. */
    public GovernanceReviewPlan(
            String planKey,
            String incidentKey,
            String title,
            GovernancePlanState state,
            GovernancePlanRisk risk,
            GovernancePlanTarget target,
            List<GovernancePlanDiff> diffs,
            String serviceKey,
            String resourceKey,
            String proposedAction,
            List<EvidenceItem> evidence,
            int evidenceCount,
            int impactedServiceCount,
            int impactedInstanceCount,
            Instant createdAt,
            Instant updatedAt) {
        this.planKey = GovernancePlatformValidators.token(planKey, "planKey");
        this.incidentKey = GovernancePlatformValidators.token(incidentKey, "incidentKey");
        this.title = GovernancePlatformValidators.label(title, "title");
        this.state = state == null ? GovernancePlanState.DRAFT : state;
        this.risk = risk == null ? GovernancePlanRisk.MEDIUM : risk;
        this.target = Objects.requireNonNull(target, "target");
        this.diffs = immutableList(diffs);
        this.serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        this.resourceKey = GovernancePlatformValidators.token(resourceKey, "resourceKey");
        this.proposedAction = GovernancePlatformValidators.label(proposedAction, "proposedAction");
        this.evidence = immutableList(evidence);
        this.evidenceCount = Math.max(0, evidenceCount);
        this.impactedServiceCount = Math.max(0, impactedServiceCount);
        this.impactedInstanceCount = Math.max(0, impactedInstanceCount);
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    /** Returns the stable plan key. */
    public String planKey() { return planKey; }
    /** Returns the source incident key. */
    public String incidentKey() { return incidentKey; }
    /** Returns the plan title. */
    public String title() { return title; }
    /** Returns the plan state. */
    public GovernancePlanState state() { return state; }
    /** Returns the plan risk. */
    public GovernancePlanRisk risk() { return risk; }
    /** Returns the review target. */
    public GovernancePlanTarget target() { return target; }
    /** Returns suggested diffs. */
    public List<GovernancePlanDiff> diffs() { return diffs; }
    /** Returns the impacted service key. */
    public String serviceKey() { return serviceKey; }
    /** Returns the primary resource key. */
    public String resourceKey() { return resourceKey; }
    /** Returns the proposed human review action. */
    public String proposedAction() { return proposedAction; }
    /** Returns retained evidence used by the plan. */
    public List<EvidenceItem> evidence() { return evidence; }
    /** Returns the evidence count. */
    public int evidenceCount() { return evidenceCount; }
    /** Returns the impacted service count. */
    public int impactedServiceCount() { return impactedServiceCount; }
    /** Returns the impacted instance count. */
    public int impactedInstanceCount() { return impactedInstanceCount; }
    /** Returns creation time. */
    public Instant createdAt() { return createdAt; }
    /** Returns last update time. */
    public Instant updatedAt() { return updatedAt; }

    private static <T> List<T> immutableList(List<T> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(items);
    }
}
