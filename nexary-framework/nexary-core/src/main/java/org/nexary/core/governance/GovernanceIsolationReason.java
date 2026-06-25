package org.nexary.core.governance;

/** Fixed low-cardinality reasons for traffic isolation and priority pressure events. */
public enum GovernanceIsolationReason {
    /** No priority isolation was applied. */
    NONE,

    /** A lower-priority rate window rejected or fell back before consuming shared capacity. */
    PRIORITY_RATE_LIMITED,

    /** A lower-priority bulkhead rejected or fell back before consuming shared capacity. */
    PRIORITY_BULKHEAD_FULL,

    /** A lower-priority request was routed to degraded fallback. */
    PRIORITY_DEGRADED,

    /** A lower-priority request was stopped by an open circuit. */
    PRIORITY_CIRCUIT_OPEN,

    /** More than one fixed traffic class used the same resource. */
    MIXED_TRAFFIC,

    /** The isolation reason was not classified. */
    UNKNOWN
}
