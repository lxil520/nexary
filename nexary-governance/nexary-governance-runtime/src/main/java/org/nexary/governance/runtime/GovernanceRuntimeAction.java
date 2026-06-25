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
    WARN
}
