package org.nexary.governance.runtime;

/** Aggregate low-cardinality summary for local instance health diagnostics. */
public final class InstanceHealthSummary {
    private final int instanceCount;
    private final long healthyCount;
    private final long suspectCount;
    private final long quarantineCandidateCount;
    private final long recoveringCount;
    private final long recoveryProbeCount;

    /** Creates an instance health summary. */
    public InstanceHealthSummary(
            int instanceCount,
            long healthyCount,
            long suspectCount,
            long quarantineCandidateCount,
            long recoveringCount,
            long recoveryProbeCount) {
        this.instanceCount = Math.max(0, instanceCount);
        this.healthyCount = Math.max(0L, healthyCount);
        this.suspectCount = Math.max(0L, suspectCount);
        this.quarantineCandidateCount = Math.max(0L, quarantineCandidateCount);
        this.recoveringCount = Math.max(0L, recoveringCount);
        this.recoveryProbeCount = Math.max(0L, recoveryProbeCount);
    }

    /** Returns an empty summary. */
    public static InstanceHealthSummary empty() {
        return new InstanceHealthSummary(0, 0L, 0L, 0L, 0L, 0L);
    }

    /** Returns known instance count. */
    public int instanceCount() {
        return instanceCount;
    }

    /** Returns instances currently healthy. */
    public long healthyCount() {
        return healthyCount;
    }

    /** Returns instances currently suspect. */
    public long suspectCount() {
        return suspectCount;
    }

    /** Returns instances currently marked as quarantine candidates. */
    public long quarantineCandidateCount() {
        return quarantineCandidateCount;
    }

    /** Returns instances currently recovering. */
    public long recoveringCount() {
        return recoveringCount;
    }

    /** Returns instances currently advised for recovery probe. */
    public long recoveryProbeCount() {
        return recoveryProbeCount;
    }
}
