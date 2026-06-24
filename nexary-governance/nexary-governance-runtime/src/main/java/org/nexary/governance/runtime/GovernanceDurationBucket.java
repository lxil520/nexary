package org.nexary.governance.runtime;

import java.time.Duration;

/** Low-cardinality duration bucket for local governance diagnostics. */
public enum GovernanceDurationBucket {
    NOT_RUN,
    LT_10_MS,
    LT_100_MS,
    LT_500_MS,
    GE_500_MS;

    /** Buckets a completed call duration without exposing exact latency. */
    public static GovernanceDurationBucket from(Duration duration) {
        if (duration == null || duration.isNegative()) {
            return NOT_RUN;
        }
        long millis = duration.toMillis();
        if (millis < 10L) {
            return LT_10_MS;
        }
        if (millis < 100L) {
            return LT_100_MS;
        }
        if (millis < 500L) {
            return LT_500_MS;
        }
        return GE_500_MS;
    }
}
