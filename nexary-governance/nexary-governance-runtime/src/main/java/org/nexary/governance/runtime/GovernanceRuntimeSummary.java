package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Aggregate low-cardinality view of the local governance runtime. */
public final class GovernanceRuntimeSummary {
    private final int resourceCount;
    private final int snapshotCount;
    private final int eventCount;
    private final long successCount;
    private final long failureCount;
    private final long rejectedCount;
    private final long fallbackCount;
    private final long cancelledCount;
    private final long blockedCount;
    private final long sentinelResourceCount;
    private final long openCircuitCount;
    private final long halfOpenCircuitCount;
    private final long degradedResourceCount;
    private final Instant lastEventAt;

    /** Creates a runtime summary. */
    public GovernanceRuntimeSummary(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            Instant lastEventAt) {
        this(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                0L,
                0L,
                0L,
                0L,
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }

    /** Creates a runtime summary with retained-event fallback counts. */
    public GovernanceRuntimeSummary(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long fallbackCount,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            Instant lastEventAt) {
        this(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                fallbackCount,
                0L,
                0L,
                0L,
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }

    /** Creates a runtime summary with retained-event fallback and cancellation counts. */
    public GovernanceRuntimeSummary(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long fallbackCount,
            long cancelledCount,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            Instant lastEventAt) {
        this(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                fallbackCount,
                cancelledCount,
                0L,
                0L,
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }

    /** Creates a runtime summary with retained-event fallback, cancellation, and engine counts. */
    public GovernanceRuntimeSummary(
            int resourceCount,
            int snapshotCount,
            int eventCount,
            long successCount,
            long failureCount,
            long rejectedCount,
            long fallbackCount,
            long cancelledCount,
            long blockedCount,
            long sentinelResourceCount,
            long openCircuitCount,
            long halfOpenCircuitCount,
            long degradedResourceCount,
            Instant lastEventAt) {
        this.resourceCount = Math.max(0, resourceCount);
        this.snapshotCount = Math.max(0, snapshotCount);
        this.eventCount = Math.max(0, eventCount);
        this.successCount = Math.max(0L, successCount);
        this.failureCount = Math.max(0L, failureCount);
        this.rejectedCount = Math.max(0L, rejectedCount);
        this.fallbackCount = Math.max(0L, fallbackCount);
        this.cancelledCount = Math.max(0L, cancelledCount);
        this.blockedCount = Math.max(0L, blockedCount);
        this.sentinelResourceCount = Math.max(0L, sentinelResourceCount);
        this.openCircuitCount = Math.max(0L, openCircuitCount);
        this.halfOpenCircuitCount = Math.max(0L, halfOpenCircuitCount);
        this.degradedResourceCount = Math.max(0L, degradedResourceCount);
        this.lastEventAt = lastEventAt;
    }

    /** Returns the number of known resource descriptors. */
    public int resourceCount() {
        return resourceCount;
    }

    /** Returns the number of runtime snapshots. */
    public int snapshotCount() {
        return snapshotCount;
    }

    /** Returns the number of retained recent events. */
    public int eventCount() {
        return eventCount;
    }

    /** Returns the retained recent-event success count. */
    public long successCount() {
        return successCount;
    }

    /** Returns the retained recent-event failure count. */
    public long failureCount() {
        return failureCount;
    }

    /** Returns the retained recent-event rejected count. */
    public long rejectedCount() {
        return rejectedCount;
    }

    /** Returns the retained recent-event fallback count. */
    public long fallbackCount() {
        return fallbackCount;
    }

    /** Returns the retained recent-event cancelled count. */
    public long cancelledCount() {
        return cancelledCount;
    }

    /** Returns the retained recent-event count blocked by a governance engine. */
    public long blockedCount() {
        return blockedCount;
    }

    /** Returns the number of known Sentinel-backed resource descriptors. */
    public long sentinelResourceCount() {
        return sentinelResourceCount;
    }

    /** Returns the number of snapshots with an open circuit. */
    public long openCircuitCount() {
        return openCircuitCount;
    }

    /** Returns the number of snapshots with a half-open circuit. */
    public long halfOpenCircuitCount() {
        return halfOpenCircuitCount;
    }

    /** Returns the number of snapshots currently using a degraded policy. */
    public long degradedResourceCount() {
        return degradedResourceCount;
    }

    /** Returns when the latest retained event was recorded, if available. */
    public Optional<Instant> lastEventAt() {
        return Optional.ofNullable(lastEventAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceRuntimeSummary)) {
            return false;
        }
        GovernanceRuntimeSummary that = (GovernanceRuntimeSummary) other;
        return resourceCount == that.resourceCount
                && snapshotCount == that.snapshotCount
                && eventCount == that.eventCount
                && successCount == that.successCount
                && failureCount == that.failureCount
                && rejectedCount == that.rejectedCount
                && fallbackCount == that.fallbackCount
                && cancelledCount == that.cancelledCount
                && blockedCount == that.blockedCount
                && sentinelResourceCount == that.sentinelResourceCount
                && openCircuitCount == that.openCircuitCount
                && halfOpenCircuitCount == that.halfOpenCircuitCount
                && degradedResourceCount == that.degradedResourceCount
                && Objects.equals(lastEventAt, that.lastEventAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resourceCount,
                snapshotCount,
                eventCount,
                successCount,
                failureCount,
                rejectedCount,
                fallbackCount,
                cancelledCount,
                blockedCount,
                sentinelResourceCount,
                openCircuitCount,
                halfOpenCircuitCount,
                degradedResourceCount,
                lastEventAt);
    }
}
