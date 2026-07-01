package org.nexary.governance.platform;

/** Local audit action recorded by the governance platform. */
public enum GovernanceAuditAction {
    /** A review plan was generated from retained evidence. */
    PLAN_GENERATED,
    /** A plan dry-run was calculated. */
    PLAN_DRY_RUN,
    /** Review material was exported. */
    PLAN_EXPORT_REVIEW,
    /** A notification route preview was rendered. */
    NOTIFICATION_PREVIEW,
    /** A notification test was attempted. */
    NOTIFICATION_TEST
}
