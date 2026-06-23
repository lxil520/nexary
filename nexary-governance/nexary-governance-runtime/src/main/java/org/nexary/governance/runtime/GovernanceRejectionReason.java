package org.nexary.governance.runtime;

/** Low-cardinality reason for the most recent local governance rejection. */
public enum GovernanceRejectionReason {
    /** No rejection has been recorded for this runtime state. */
    NONE,

    /** The call was rejected because its deadline had already expired. */
    DEADLINE_EXPIRED,

    /** The policy is explicitly degraded and routes calls to fallback. */
    DEGRADED,

    /** The rate-limit window has no remaining permits. */
    RATE_LIMITED,

    /** The bulkhead has no remaining concurrency permits. */
    CONCURRENCY_LIMITED,

    /** The circuit is open and rejects calls without running the action. */
    CIRCUIT_OPEN,

    /** The circuit is half-open and all probe permits are already in use. */
    HALF_OPEN_LIMITED
}
