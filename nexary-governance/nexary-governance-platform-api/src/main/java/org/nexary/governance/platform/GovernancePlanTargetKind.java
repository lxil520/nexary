package org.nexary.governance.platform;

/** Target type of a local governance review plan. */
public enum GovernancePlanTargetKind {
    /** Sentinel resource threshold or circuit-breaker candidate. */
    SENTINEL_RESOURCE,
    /** Gateway route timeout, retry, or disconnect-protection candidate. */
    GATEWAY_ROUTE,
    /** Instance isolation candidate for human review. */
    INSTANCE_CANDIDATE,
    /** Alert threshold adjustment candidate. */
    ALERT_THRESHOLD,
    /** Ownership or resource mapping candidate. */
    OWNERSHIP_MAPPING
}
