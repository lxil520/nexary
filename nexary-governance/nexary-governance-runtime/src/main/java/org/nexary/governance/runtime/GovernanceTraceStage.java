package org.nexary.governance.runtime;

/** Fixed low-cardinality stage for one local fault trace step. */
public enum GovernanceTraceStage {
    /** Inbound request or top-level call boundary. */
    REQUEST,

    /** Governance decision such as reject, block, fallback, or cancellation. */
    GOVERNANCE,

    /** Downstream call result. */
    DOWNSTREAM,

    /** Cache access. */
    CACHE,

    /** Messaging publish or consume step. */
    MESSAGING,

    /** Job scheduling or execution step. */
    JOB,

    /** Local instance health diagnostic step. */
    INSTANCE_HEALTH,

    /** Retry decision or retry-stop propagation step. */
    RETRY
}
