package org.nexary.governance.runtime;

/** Low-cardinality action recorded by local governance diagnostics. */
public enum GovernanceRuntimeAction {
    /** The protected action was allowed to run. */
    EXECUTE,

    /** The protected action was rejected without fallback. */
    REJECT,

    /** The protected action was rejected and the supplied fallback was used. */
    FALLBACK
}
