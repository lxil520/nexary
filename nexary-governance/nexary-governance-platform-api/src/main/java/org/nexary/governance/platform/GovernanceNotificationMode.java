package org.nexary.governance.platform;

/** Execution mode of a local notification route. */
public enum GovernanceNotificationMode {
    /** Route can render dry-run preview material only. */
    DRY_RUN,
    /** Route can attempt explicitly marked test delivery. */
    TEST,
    /** Route is disabled. */
    DISABLED
}
