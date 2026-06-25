package org.nexary.core.context;

/** Low-cardinality reason for cooperative request cancellation. */
public enum CancellationReason {
    /** The request has not been cancelled. */
    NONE,

    /** The client disconnected before the work completed. */
    CLIENT_DISCONNECTED,

    /** The request deadline expired. */
    DEADLINE_EXPIRED,

    /** An upstream caller already cancelled the request. */
    UPSTREAM_CANCELLED,

    /** A local operator or test cancelled the request explicitly. */
    MANUAL,

    /** The local process is shutting down. */
    SHUTDOWN
}
