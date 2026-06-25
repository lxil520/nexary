package org.nexary.governance.runtime;

/** Low-cardinality block reason reported by external governance engines. */
public enum GovernanceBlockReason {
    /** No block has been observed. */
    NONE,

    /** A rate limit rejected the call. */
    RATE_LIMITED,

    /** A concurrency limit rejected the call. */
    BULKHEAD_FULL,

    /** An open circuit rejected the call. */
    CIRCUIT_OPEN,

    /** A degraded policy routed the call away from the primary action. */
    DEGRADED,

    /** The engine blocked the call for a reason Nexary does not classify yet. */
    UNKNOWN
}
