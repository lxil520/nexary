package org.nexary.governance.platform;

/** Lifecycle state of a local governance review plan. */
public enum GovernancePlanState {
    /** Plan was generated from evidence and has not been reviewed. */
    DRAFT,
    /** A dry-run was calculated for the plan. */
    DRY_RUN,
    /** Review material was exported from the plan. */
    REVIEW_EXPORTED,
    /** The plan is blocked by missing or risky evidence. */
    BLOCKED
}
