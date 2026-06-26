package org.nexary.governance.runtime;

/** Low-cardinality action recorded by local governance diagnostics. */
public enum GovernanceRuntimeAction {
    /** The protected action was allowed to run. */
    EXECUTE,

    /** The protected action was rejected without fallback. */
    REJECT,

    /** The protected action was rejected and the supplied fallback was used. */
    FALLBACK,

    /** The protected action was cancelled before useful work should continue. */
    CANCEL,

    /** A retry loop was explicitly told not to schedule another attempt. */
    STOP_RETRY,

    /** A low-cardinality warning was recorded without mutating policy. */
    WARN,

    /** A downstream instance became locally suspect. */
    INSTANCE_SUSPECT,

    /** A downstream instance became a local quarantine candidate. */
    QUARANTINE_CANDIDATE,

    /** A downstream instance is being observed for recovery. */
    RECOVERY_PROBE,

    /** A downstream instance recovered to healthy state. */
    INSTANCE_RECOVERED
}
