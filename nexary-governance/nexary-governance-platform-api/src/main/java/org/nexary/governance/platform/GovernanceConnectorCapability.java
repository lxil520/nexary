package org.nexary.governance.platform;

/** Capability exposed by a governance connector configuration. */
public enum GovernanceConnectorCapability {
    /** Reads service topology evidence. */
    READ_TOPOLOGY,
    /** Reads trace and span evidence. */
    READ_TRACES,
    /** Reads alert evidence. */
    READ_ALERTS,
    /** Reads metric series. */
    READ_METRICS,
    /** Reads gateway route and upstream state. */
    READ_GATEWAY_ROUTES,
    /** Reads Sentinel resources, blocks, and circuit state. */
    READ_SENTINEL_STATE,
    /** Calculates review-only plan material. */
    DRY_RUN_PLAN,
    /** Sends explicitly marked notification tests. */
    TEST_NOTIFICATION,
    /** Marks that production writes are disabled. */
    WRITE_DISABLED
}
