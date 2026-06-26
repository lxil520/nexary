package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Aggregate low-cardinality view of retained local fault traces. */
public final class GovernanceFaultTraceSummary {
    private final long traceCount;
    private final long stoppedCount;
    private final long blockedCount;
    private final long cancelledCount;
    private final long retryStoppedCount;
    private final long instanceRelatedCount;
    private final Map<String, Long> topStopReasons;

    /** Creates a fault trace summary. */
    public GovernanceFaultTraceSummary(
            long traceCount,
            long stoppedCount,
            long blockedCount,
            long cancelledCount,
            long retryStoppedCount,
            long instanceRelatedCount,
            Map<String, Long> topStopReasons) {
        this.traceCount = Math.max(0L, traceCount);
        this.stoppedCount = Math.max(0L, stoppedCount);
        this.blockedCount = Math.max(0L, blockedCount);
        this.cancelledCount = Math.max(0L, cancelledCount);
        this.retryStoppedCount = Math.max(0L, retryStoppedCount);
        this.instanceRelatedCount = Math.max(0L, instanceRelatedCount);
        this.topStopReasons = immutableCounts(topStopReasons);
    }

    /** Returns an empty trace summary. */
    public static GovernanceFaultTraceSummary empty() {
        return new GovernanceFaultTraceSummary(0L, 0L, 0L, 0L, 0L, 0L, Collections.emptyMap());
    }

    /** Returns retained trace count. */
    public long traceCount() {
        return traceCount;
    }

    /** Returns traces with a non-success stop reason. */
    public long stoppedCount() {
        return stoppedCount;
    }

    /** Returns traces stopped by local reject or engine block. */
    public long blockedCount() {
        return blockedCount;
    }

    /** Returns traces stopped by cancellation or deadline. */
    public long cancelledCount() {
        return cancelledCount;
    }

    /** Returns traces stopped by retry-stop propagation. */
    public long retryStoppedCount() {
        return retryStoppedCount;
    }

    /** Returns traces that involved instance health diagnostics. */
    public long instanceRelatedCount() {
        return instanceRelatedCount;
    }

    /** Returns retained trace counts by low-cardinality stop reason. */
    public Map<String, Long> topStopReasons() {
        return topStopReasons;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceFaultTraceSummary)) {
            return false;
        }
        GovernanceFaultTraceSummary that = (GovernanceFaultTraceSummary) other;
        return traceCount == that.traceCount
                && stoppedCount == that.stoppedCount
                && blockedCount == that.blockedCount
                && cancelledCount == that.cancelledCount
                && retryStoppedCount == that.retryStoppedCount
                && instanceRelatedCount == that.instanceRelatedCount
                && topStopReasons.equals(that.topStopReasons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                traceCount,
                stoppedCount,
                blockedCount,
                cancelledCount,
                retryStoppedCount,
                instanceRelatedCount,
                topStopReasons);
    }

    private static Map<String, Long> immutableCounts(Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> copy = new LinkedHashMap<>();
        counts.forEach((key, value) -> {
            if (key != null && value != null) {
                copy.put(key, Math.max(0L, value));
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
