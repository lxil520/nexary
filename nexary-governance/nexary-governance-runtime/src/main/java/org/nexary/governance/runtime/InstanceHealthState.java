package org.nexary.governance.runtime;

/** Fixed low-cardinality state for local instance health diagnostics. */
public enum InstanceHealthState {
    /** The instance has no local anomaly. */
    HEALTHY,

    /** The instance has one or more abnormal local windows. */
    SUSPECT,

    /** The instance is a local quarantine candidate, but Nexary has not removed traffic automatically. */
    QUARANTINE_CANDIDATE,

    /** The instance is being observed for recovery after previous abnormal windows. */
    RECOVERING
}
