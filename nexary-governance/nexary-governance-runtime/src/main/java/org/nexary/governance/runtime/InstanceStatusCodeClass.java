package org.nexary.governance.runtime;

/** Fixed HTTP status-code class used by instance health signals. */
public enum InstanceStatusCodeClass {
    /** No HTTP status was available. */
    NONE,

    /** HTTP 2xx status class. */
    HTTP_2XX,

    /** HTTP 3xx status class. */
    HTTP_3XX,

    /** HTTP 4xx status class. */
    HTTP_4XX,

    /** HTTP 5xx status class. */
    HTTP_5XX,

    /** A status was available but did not map cleanly to a standard class. */
    UNKNOWN
}
