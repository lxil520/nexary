package org.nexary.governance.runtime;

/** Fixed low-cardinality signal types used by instance health detection. */
public enum InstanceHealthSignalType {
    /** A connection attempt timed out. */
    CONNECT_TIMEOUT,

    /** The connection was reset. */
    CONNECTION_RESET,

    /** A read operation timed out after connecting. */
    READ_TIMEOUT,

    /** The instance returned a server-error status class. */
    SERVER_ERROR,

    /** The instance completed but exceeded the slow-call threshold. */
    SLOW_CALL,

    /** The instance status-code distribution skewed away from peer instances. */
    STATUS_CODE_SKEW
}
