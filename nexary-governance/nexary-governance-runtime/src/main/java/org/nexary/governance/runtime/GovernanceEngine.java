package org.nexary.governance.runtime;

/** Low-cardinality execution engine used by governance diagnostics. */
public enum GovernanceEngine {
    /** Nexary's in-process local governance runtime. */
    LOCAL,

    /** Alibaba Sentinel-backed governance runtime. */
    SENTINEL
}
