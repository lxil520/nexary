package org.nexary.console.api;

/**
 * Read-only console view of one recent governance runtime event.
 */
public final class ConsoleEventItem {
    private final String resourceKey;
    private final String action;
    private final String outcome;
    private final String rejectionReason;
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
            String circuitState,
            String timestamp,
            String durationBucket) {
        this.resourceKey = resourceKey;
        this.action = action;
        this.outcome = outcome;
        this.rejectionReason = rejectionReason;
        this.circuitState = circuitState;
        this.timestamp = timestamp;
        this.durationBucket = durationBucket;
    }

    /** Returns the stable governed resource key. */
    public String getResourceKey() {
        return resourceKey;
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
