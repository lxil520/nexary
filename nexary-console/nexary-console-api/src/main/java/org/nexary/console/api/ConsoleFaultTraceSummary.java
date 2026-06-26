package org.nexary.console.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Aggregate read-only console view of retained local fault traces. */
public final class ConsoleFaultTraceSummary {
    private final long traceCount;
    private final long stoppedCount;
    private final long blockedCount;
    private final long cancelledCount;
    private final long retryStoppedCount;
    private final long instanceRelatedCount;
    private final Map<String, Long> topStopReasons;

    /** Creates a trace summary from bounded aggregate counters. */
    public ConsoleFaultTraceSummary(
            long traceCount,
            long stoppedCount,
            long blockedCount,
            long cancelledCount,
            long retryStoppedCount,
            long instanceRelatedCount,
            Map<String, Long> topStopReasons) {
        this.traceCount = traceCount;
        this.stoppedCount = stoppedCount;
        this.blockedCount = blockedCount;
        this.cancelledCount = cancelledCount;
        this.retryStoppedCount = retryStoppedCount;
        this.instanceRelatedCount = instanceRelatedCount;
        this.topStopReasons = topStopReasons == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(topStopReasons));
    }

    /** Returns retained trace count. */
    public long getTraceCount() {
        return traceCount;
    }

    /** Returns retained stopped trace count. */
    public long getStoppedCount() {
        return stoppedCount;
    }

    /** Returns traces blocked or rejected by governance. */
    public long getBlockedCount() {
        return blockedCount;
    }

    /** Returns traces cancelled or stopped by deadline. */
    public long getCancelledCount() {
        return cancelledCount;
    }

    /** Returns traces stopped by retry governance. */
    public long getRetryStoppedCount() {
        return retryStoppedCount;
    }

    /** Returns traces involving local instance health diagnostics. */
    public long getInstanceRelatedCount() {
        return instanceRelatedCount;
    }

    /** Returns counts by low-cardinality stop reason. */
    public Map<String, Long> getTopStopReasons() {
        return topStopReasons;
    }
}
