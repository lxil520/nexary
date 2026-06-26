package org.nexary.console.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregate read-only summary shown by the Nexary console.
 */
public final class ConsoleSummaryResponse {
    private final int resourceCount;
    private final int snapshotCount;
    private final int eventCount;
    private final long successCount;
    private final long failureCount;
    private final long rejectedCount;
    private final long cancelledCount;
    private final long fallbackCount;
    private final long retryStoppedCount;
    private final long blockedCount;
    private final long isolatedCount;
    private final long sentinelResourceCount;
    private final long instanceSuspectCount;
    private final long quarantineCandidateCount;
    private final long recoveryProbeCount;
    private final Map<String, Long> trafficClassCounts;
    private final Map<String, Long> priorityCounts;
    private final long openCircuitCount;
    private final long halfOpenCircuitCount;
    private final long degradedResourceCount;
    private final String lastEventAt;

    /**
     * Creates a summary response from bounded aggregate counters.
     */
    public ConsoleSummaryResponse(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long cancelledCount,
            long fallbackCount,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            String lastEventAt) {
        this(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                cancelledCount,
                fallbackCount,
                0L,
                0L,
                0L,
                0L,
                Collections.emptyMap(),
                Collections.emptyMap(),
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }

    /**
     * Creates a summary response from bounded aggregate counters, including engine counters.
     */
    public ConsoleSummaryResponse(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long cancelledCount,
            long fallbackCount,
            long blockedCount,
            long sentinelResourceCount,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            String lastEventAt) {
        this(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                cancelledCount,
                fallbackCount,
                0L,
                blockedCount,
                0L,
                sentinelResourceCount,
                Collections.emptyMap(),
                Collections.emptyMap(),
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }

    /**
     * Creates a summary response from bounded aggregate counters, including retry-stop and engine counters.
     */
    public ConsoleSummaryResponse(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long cancelledCount,
            long fallbackCount,
            long retryStoppedCount,
            long blockedCount,
            long sentinelResourceCount,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            String lastEventAt) {
        this(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                cancelledCount,
                fallbackCount,
                retryStoppedCount,
                blockedCount,
                0L,
                sentinelResourceCount,
                Collections.emptyMap(),
                Collections.emptyMap(),
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }

    /**
     * Creates a summary response from bounded aggregate counters, including priority isolation distributions.
     */
    public ConsoleSummaryResponse(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long cancelledCount,
            long fallbackCount,
            long retryStoppedCount,
            long blockedCount,
            long isolatedCount,
            long sentinelResourceCount,
            Map<String, Long> trafficClassCounts,
            Map<String, Long> priorityCounts,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            String lastEventAt) {
        this(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                cancelledCount,
                fallbackCount,
                retryStoppedCount,
                blockedCount,
                isolatedCount,
                sentinelResourceCount,
                0L,
                0L,
                0L,
                trafficClassCounts,
                priorityCounts,
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }

    /**
     * Creates a summary response from bounded aggregate counters, including instance health counters.
     */
    public ConsoleSummaryResponse(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long cancelledCount,
            long fallbackCount,
            long retryStoppedCount,
            long blockedCount,
            long isolatedCount,
            long sentinelResourceCount,
            long instanceSuspectCount,
            long quarantineCandidateCount,
            long recoveryProbeCount,
            Map<String, Long> trafficClassCounts,
            Map<String, Long> priorityCounts,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            String lastEventAt) {
        this.resourceCount = resourceCount;
        this.snapshotCount = snapshotCount;
        this.eventCount = eventCount;
        this.successCount = successCount;
        this.failureCount = failureCount;
        this.rejectedCount = rejectedCount;
        this.cancelledCount = cancelledCount;
        this.fallbackCount = fallbackCount;
        this.retryStoppedCount = retryStoppedCount;
        this.blockedCount = blockedCount;
        this.isolatedCount = isolatedCount;
        this.sentinelResourceCount = sentinelResourceCount;
        this.instanceSuspectCount = instanceSuspectCount;
        this.quarantineCandidateCount = quarantineCandidateCount;
        this.recoveryProbeCount = recoveryProbeCount;
        this.trafficClassCounts = immutableCounts(trafficClassCounts);
        this.priorityCounts = immutableCounts(priorityCounts);
        this.openCircuitCount = openCircuitCount;
        this.halfOpenCircuitCount = halfOpenCircuitCount;
        this.degradedResourceCount = degradedResourceCount;
        this.lastEventAt = lastEventAt;
    }

    /** Returns the number of known resource descriptors. */
    public int getResourceCount() {
        return resourceCount;
    }

    /** Returns the number of runtime snapshots. */
    public int getSnapshotCount() {
        return snapshotCount;
    }

    /** Returns the number of retained recent events. */
    public int getEventCount() {
        return eventCount;
    }

    /** Returns the retained recent-event success count. */
    public long getSuccessCount() {
        return successCount;
    }

    /** Returns the retained recent-event failure count. */
    public long getFailureCount() {
        return failureCount;
    }

    /** Returns the retained recent-event rejected count. */
    public long getRejectedCount() {
        return rejectedCount;
    }

    /** Returns the retained recent-event cancelled count. */
    public long getCancelledCount() {
        return cancelledCount;
    }

    /** Returns the retained recent-event fallback count. */
    public long getFallbackCount() {
        return fallbackCount;
    }

    /** Returns the retained recent-event count that stopped retry propagation. */
    public long getRetryStoppedCount() {
        return retryStoppedCount;
    }

    /** Returns the retained recent-event count blocked by a governance engine. */
    public long getBlockedCount() {
        return blockedCount;
    }

    /** Returns the retained recent-event count isolated by priority policy. */
    public long getIsolatedCount() {
        return isolatedCount;
    }

    /** Returns the number of resources currently backed by Sentinel. */
    public long getSentinelResourceCount() {
        return sentinelResourceCount;
    }

    /** Returns instances currently suspect in local diagnostics. */
    public long getInstanceSuspectCount() {
        return instanceSuspectCount;
    }

    /** Returns instances currently marked as local quarantine candidates. */
    public long getQuarantineCandidateCount() {
        return quarantineCandidateCount;
    }

    /** Returns instances currently advised for recovery probing. */
    public long getRecoveryProbeCount() {
        return recoveryProbeCount;
    }

    /** Returns retained recent-event counts by fixed traffic class. */
    public Map<String, Long> getTrafficClassCounts() {
        return trafficClassCounts;
    }

    /** Returns retained recent-event counts by fixed priority bucket. */
    public Map<String, Long> getPriorityCounts() {
        return priorityCounts;
    }

    /** Returns the number of snapshots with an open circuit. */
    public long getOpenCircuitCount() {
        return openCircuitCount;
    }

    /** Returns the number of snapshots with a half-open circuit. */
    public long getHalfOpenCircuitCount() {
        return halfOpenCircuitCount;
    }

    /** Returns the number of snapshots currently using a degraded policy. */
    public long getDegradedResourceCount() {
        return degradedResourceCount;
    }

    /** Returns when the latest retained event was recorded, if available. */
    public String getLastEventAt() {
        return lastEventAt;
    }

    private static Map<String, Long> immutableCounts(Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }
}
