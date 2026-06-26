package org.nexary.governance.runtime;

/** Fixed low-cardinality reason for marking an instance as suspect or quarantine candidate. */
public enum InstanceQuarantineReason {
    /** No anomaly is currently known. */
    NONE,

    /** Connection timeouts spiked for this instance. */
    CONNECT_TIMEOUT_SPIKE,

    /** Connection resets spiked for this instance. */
    RESET_SPIKE,

    /** Read timeouts spiked for this instance. */
    READ_TIMEOUT_SPIKE,

    /** Server-error ratio exceeded the configured threshold. */
    SERVER_ERROR_RATIO,

    /** Slow-call ratio exceeded the configured threshold. */
    SLOW_RATIO,

    /** Status-code class distribution is skewed compared with peer instances. */
    STATUS_CODE_SKEW
}
