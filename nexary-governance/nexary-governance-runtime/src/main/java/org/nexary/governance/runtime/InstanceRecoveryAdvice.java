package org.nexary.governance.runtime;

/** Fixed low-cardinality advice derived from local instance health detection. */
public enum InstanceRecoveryAdvice {
    /** No recovery action is currently advised. */
    NONE,

    /** Slow down use of this instance locally. */
    BACKOFF,

    /** Treat this instance as a quarantine candidate for manual or provider-side action. */
    QUARANTINE_CANDIDATE,

    /** A human or external platform must decide the next action. */
    MANUAL_ACTION_REQUIRED,

    /** Probe the instance with cautious traffic before considering it healthy. */
    RECOVERY_PROBE
}
