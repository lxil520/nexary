package org.nexary.console.api;

/**
 * Read-only console view of one recent governance runtime event.
 */
public final class ConsoleEventItem {
    private final String resourceKey;
    private final String engine;
    private final String trafficClass;
    private final String priority;
    private final String action;
    private final String outcome;
    private final String rejectionReason;
    private final String isolationReason;
    private final String blockReason;
    private final String cancellationReason;
    private final String retryStopReason;
    private final String circuitState;
    private final String timestamp;
    private final String durationBucket;

    /**
     * Creates an event item from low-cardinality event fields.
     */
    public ConsoleEventItem(
            String resourceKey,
            String action,
            String outcome,
            String rejectionReason,
            String cancellationReason,
            String circuitState,
            String timestamp,
            String durationBucket) {
        this(resourceKey, null, action, outcome, rejectionReason, null, cancellationReason, circuitState, timestamp, durationBucket);
    }

    /**
     * Creates an event item from low-cardinality event fields, including the governance engine.
     */
    public ConsoleEventItem(
            String resourceKey,
            String engine,
            String action,
            String outcome,
            String rejectionReason,
            String blockReason,
            String cancellationReason,
            String circuitState,
            String timestamp,
            String durationBucket) {
        this(
                resourceKey,
                engine,
                "online",
                "normal",
                action,
                outcome,
                rejectionReason,
                "NONE",
                blockReason,
                cancellationReason,
                null,
                circuitState,
                timestamp,
                durationBucket);
    }

    /**
     * Creates an event item from low-cardinality event fields, including retry-stop metadata.
     */
    public ConsoleEventItem(
            String resourceKey,
            String engine,
            String action,
            String outcome,
            String rejectionReason,
            String blockReason,
            String cancellationReason,
            String retryStopReason,
            String circuitState,
            String timestamp,
            String durationBucket) {
        this(
                resourceKey,
                engine,
                "online",
                "normal",
                action,
                outcome,
                rejectionReason,
                "NONE",
                blockReason,
                cancellationReason,
                retryStopReason,
                circuitState,
                timestamp,
                durationBucket);
    }

    /**
     * Creates an event item from low-cardinality event fields, including traffic and isolation metadata.
     */
    public ConsoleEventItem(
            String resourceKey,
            String engine,
            String trafficClass,
            String priority,
            String action,
            String outcome,
            String rejectionReason,
            String isolationReason,
            String blockReason,
            String cancellationReason,
            String retryStopReason,
            String circuitState,
            String timestamp,
            String durationBucket) {
        this.resourceKey = resourceKey;
        this.engine = engine;
        this.trafficClass = trafficClass;
        this.priority = priority;
        this.action = action;
        this.outcome = outcome;
        this.rejectionReason = rejectionReason;
        this.isolationReason = isolationReason;
        this.blockReason = blockReason;
        this.cancellationReason = cancellationReason;
        this.retryStopReason = retryStopReason;
        this.circuitState = circuitState;
        this.timestamp = timestamp;
        this.durationBucket = durationBucket;
    }

    /** Returns the stable governed resource key. */
    public String getResourceKey() {
        return resourceKey;
    }

    /** Returns the governance engine that produced this event. */
    public String getEngine() {
        return engine;
    }

    /** Returns the fixed low-cardinality traffic class. */
    public String getTrafficClass() {
        return trafficClass;
    }

    /** Returns the fixed low-cardinality priority bucket. */
    public String getPriority() {
        return priority;
    }

    /** Returns the low-cardinality runtime action. */
    public String getAction() {
        return action;
    }

    /** Returns the low-cardinality call outcome. */
    public String getOutcome() {
        return outcome;
    }

    /** Returns the low-cardinality rejection reason. */
    public String getRejectionReason() {
        return rejectionReason;
    }

    /** Returns why priority isolation was applied, if any. */
    public String getIsolationReason() {
        return isolationReason;
    }

    /** Returns the low-cardinality engine block reason, if any. */
    public String getBlockReason() {
        return blockReason;
    }

    /** Returns the low-cardinality cancellation reason. */
    public String getCancellationReason() {
        return cancellationReason;
    }

    /** Returns the low-cardinality reason another retry should not be scheduled. */
    public String getRetryStopReason() {
        return retryStopReason;
    }

    /** Returns the circuit state visible after this event. */
    public String getCircuitState() {
        return circuitState;
    }

    /** Returns when this event was recorded. */
    public String getTimestamp() {
        return timestamp;
    }

    /** Returns the coarse duration bucket for this event. */
    public String getDurationBucket() {
        return durationBucket;
    }
}
