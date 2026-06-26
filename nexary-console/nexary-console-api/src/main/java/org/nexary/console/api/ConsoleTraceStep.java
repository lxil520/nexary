package org.nexary.console.api;

/** Read-only console view of one local fault trace step. */
public final class ConsoleTraceStep {
    private final String stage;
    private final String resourceKey;
    private final String action;
    private final String outcome;
    private final String durationBucket;
    private final String timestamp;
    private final String rejectionReason;
    private final String blockReason;
    private final String cancellationReason;
    private final String retryStopReason;
    private final String isolationReason;
    private final String instanceHealthState;
    private final String quarantineReason;

    /** Creates a trace step from low-cardinality diagnostic fields. */
    public ConsoleTraceStep(
            String stage,
            String resourceKey,
            String action,
            String outcome,
            String durationBucket,
            String timestamp,
            String rejectionReason,
            String blockReason,
            String cancellationReason,
            String retryStopReason,
            String isolationReason,
            String instanceHealthState,
            String quarantineReason) {
        this.stage = stage;
        this.resourceKey = resourceKey;
        this.action = action;
        this.outcome = outcome;
        this.durationBucket = durationBucket;
        this.timestamp = timestamp;
        this.rejectionReason = rejectionReason;
        this.blockReason = blockReason;
        this.cancellationReason = cancellationReason;
        this.retryStopReason = retryStopReason;
        this.isolationReason = isolationReason;
        this.instanceHealthState = instanceHealthState;
        this.quarantineReason = quarantineReason;
    }

    /** Returns the fixed trace stage. */
    public String getStage() {
        return stage;
    }

    /** Returns the stable governed resource key. */
    public String getResourceKey() {
        return resourceKey;
    }

    /** Returns the low-cardinality runtime action. */
    public String getAction() {
        return action;
    }

    /** Returns the low-cardinality runtime outcome. */
    public String getOutcome() {
        return outcome;
    }

    /** Returns the coarse duration bucket. */
    public String getDurationBucket() {
        return durationBucket;
    }

    /** Returns when this trace step was recorded. */
    public String getTimestamp() {
        return timestamp;
    }

    /** Returns the low-cardinality rejection reason, if any. */
    public String getRejectionReason() {
        return rejectionReason;
    }

    /** Returns the low-cardinality engine block reason, if any. */
    public String getBlockReason() {
        return blockReason;
    }

    /** Returns the low-cardinality cancellation reason, if any. */
    public String getCancellationReason() {
        return cancellationReason;
    }

    /** Returns the low-cardinality retry-stop reason, if any. */
    public String getRetryStopReason() {
        return retryStopReason;
    }

    /** Returns the low-cardinality priority isolation reason, if any. */
    public String getIsolationReason() {
        return isolationReason;
    }

    /** Returns the local instance health state for this step. */
    public String getInstanceHealthState() {
        return instanceHealthState;
    }

    /** Returns the local instance quarantine reason for this step. */
    public String getQuarantineReason() {
        return quarantineReason;
    }
}
