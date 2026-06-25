package org.nexary.console.api;

/**
 * Read-only runtime counters shown by the Nexary console for one governed resource.
 */
public final class ConsoleRuntimeSnapshot {
    private final String resourceKey;
    private final String engine;
    private final String priority;
    private final String circuitState;
    private final int windowCalls;
    private final int windowFailures;
    private final int windowSlowCalls;
    private final int consecutiveFailures;
    private final long totalRejections;
    private final String lastRejectionReason;
    private final String lastBlockReason;
    private final String lastCancellationReason;
    private final String openUntil;
    private final int activeConcurrency;
    private final int maxConcurrency;
    private final int maxRequestsPerWindow;
    private final String rateLimitWindow;
    private final boolean degraded;
    private final int minimumRequests;
    private final Double failureRateThreshold;
    private final Double slowCallThreshold;
    private final String slowCallDuration;
    private final String openStateDuration;
    private final int halfOpenMaxCalls;
    private final int slidingWindowSize;
    private final String slidingWindowDuration;
    private final int consecutiveFailureThreshold;
    private final String lastStateTransitionAt;
    private final String lastOutcome;
    private final String lastOutcomeAt;

    /**
     * Creates a runtime snapshot with low-cardinality counters and state labels.
     */
    public ConsoleRuntimeSnapshot(
            String resourceKey,
            String priority,
            String circuitState,
            int windowCalls,
            int windowFailures,
            int windowSlowCalls,
            int consecutiveFailures,
            long totalRejections,
            String lastRejectionReason,
            String lastCancellationReason,
            String openUntil,
            int activeConcurrency,
            int maxConcurrency,
            int maxRequestsPerWindow,
            String rateLimitWindow,
            boolean degraded,
            int minimumRequests,
            Double failureRateThreshold,
            Double slowCallThreshold,
            String slowCallDuration,
            String openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            String slidingWindowDuration,
            int consecutiveFailureThreshold,
            String lastStateTransitionAt,
            String lastOutcome,
            String lastOutcomeAt) {
        this(
                resourceKey,
                null,
                priority,
                circuitState,
                windowCalls,
                windowFailures,
                windowSlowCalls,
                consecutiveFailures,
                totalRejections,
                lastRejectionReason,
                null,
                lastCancellationReason,
                openUntil,
                activeConcurrency,
                maxConcurrency,
                maxRequestsPerWindow,
                rateLimitWindow,
                degraded,
                minimumRequests,
                failureRateThreshold,
                slowCallThreshold,
                slowCallDuration,
                openStateDuration,
                halfOpenMaxCalls,
                slidingWindowSize,
                slidingWindowDuration,
                consecutiveFailureThreshold,
                lastStateTransitionAt,
                lastOutcome,
                lastOutcomeAt);
    }

    /**
     * Creates a runtime snapshot with low-cardinality counters, state labels, and engine state.
     */
    public ConsoleRuntimeSnapshot(
            String resourceKey,
            String engine,
            String priority,
            String circuitState,
            int windowCalls,
            int windowFailures,
            int windowSlowCalls,
            int consecutiveFailures,
            long totalRejections,
            String lastRejectionReason,
            String lastBlockReason,
            String lastCancellationReason,
            String openUntil,
            int activeConcurrency,
            int maxConcurrency,
            int maxRequestsPerWindow,
            String rateLimitWindow,
            boolean degraded,
            int minimumRequests,
            Double failureRateThreshold,
            Double slowCallThreshold,
            String slowCallDuration,
            String openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            String slidingWindowDuration,
            int consecutiveFailureThreshold,
            String lastStateTransitionAt,
            String lastOutcome,
            String lastOutcomeAt) {
        this.resourceKey = resourceKey;
        this.engine = engine;
        this.priority = priority;
        this.circuitState = circuitState;
        this.windowCalls = windowCalls;
        this.windowFailures = windowFailures;
        this.windowSlowCalls = windowSlowCalls;
        this.consecutiveFailures = consecutiveFailures;
        this.totalRejections = totalRejections;
        this.lastRejectionReason = lastRejectionReason;
        this.lastBlockReason = lastBlockReason;
        this.lastCancellationReason = lastCancellationReason;
        this.openUntil = openUntil;
        this.activeConcurrency = activeConcurrency;
        this.maxConcurrency = maxConcurrency;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.rateLimitWindow = rateLimitWindow;
        this.degraded = degraded;
        this.minimumRequests = minimumRequests;
        this.failureRateThreshold = failureRateThreshold;
        this.slowCallThreshold = slowCallThreshold;
        this.slowCallDuration = slowCallDuration;
        this.openStateDuration = openStateDuration;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
        this.slidingWindowSize = slidingWindowSize;
        this.slidingWindowDuration = slidingWindowDuration;
        this.consecutiveFailureThreshold = consecutiveFailureThreshold;
        this.lastStateTransitionAt = lastStateTransitionAt;
        this.lastOutcome = lastOutcome;
        this.lastOutcomeAt = lastOutcomeAt;
    }

    /** Returns the stable governed resource key. */
    public String getResourceKey() {
        return resourceKey;
    }

    /** Returns the governance engine that produced this snapshot. */
    public String getEngine() {
        return engine;
    }

    /** Returns the request priority bucket for this runtime state. */
    public String getPriority() {
        return priority;
    }

    /** Returns the current circuit state. */
    public String getCircuitState() {
        return circuitState;
    }

    /** Returns completed calls retained in the current window. */
    public int getWindowCalls() {
        return windowCalls;
    }

    /** Returns failed completed calls retained in the current window. */
    public int getWindowFailures() {
        return windowFailures;
    }

    /** Returns slow completed calls retained in the current window. */
    public int getWindowSlowCalls() {
        return windowSlowCalls;
    }

    /** Returns the current consecutive failed-call count. */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /** Returns the total local governance rejections observed for this state. */
    public long getTotalRejections() {
        return totalRejections;
    }

    /** Returns the low-cardinality reason for the latest local rejection. */
    public String getLastRejectionReason() {
        return lastRejectionReason;
    }

    /** Returns the latest low-cardinality engine block reason. */
    public String getLastBlockReason() {
        return lastBlockReason;
    }

    /** Returns the low-cardinality reason for the latest cooperative cancellation. */
    public String getLastCancellationReason() {
        return lastCancellationReason;
    }

    /** Returns when the open circuit may begin half-open probing, if known. */
    public String getOpenUntil() {
        return openUntil;
    }

    /** Returns the number of calls currently running for this resource state. */
    public int getActiveConcurrency() {
        return activeConcurrency;
    }

    /** Returns the configured maximum concurrent calls. */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /** Returns the configured rate-limit allowance per window. */
    public int getMaxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    /** Returns the configured rate-limit accounting window. */
    public String getRateLimitWindow() {
        return rateLimitWindow;
    }

    /** Returns whether the current policy routes calls to fallback without running the action. */
    public boolean isDegraded() {
        return degraded;
    }

    /** Returns the configured minimum completed calls before circuit percentage checks run. */
    public int getMinimumRequests() {
        return minimumRequests;
    }

    /** Returns the configured failure-rate threshold percentage, or null when disabled. */
    public Double getFailureRateThreshold() {
        return failureRateThreshold;
    }

    /** Returns the configured slow-call threshold percentage, or null when disabled. */
    public Double getSlowCallThreshold() {
        return slowCallThreshold;
    }

    /** Returns the configured slow-call duration threshold, if set. */
    public String getSlowCallDuration() {
        return slowCallDuration;
    }

    /** Returns the configured open-state duration. */
    public String getOpenStateDuration() {
        return openStateDuration;
    }

    /** Returns the configured number of half-open probe calls. */
    public int getHalfOpenMaxCalls() {
        return halfOpenMaxCalls;
    }

    /** Returns the configured sliding-window size. */
    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    /** Returns the configured sliding-window duration. */
    public String getSlidingWindowDuration() {
        return slidingWindowDuration;
    }

    /** Returns the configured consecutive-failure threshold. */
    public int getConsecutiveFailureThreshold() {
        return consecutiveFailureThreshold;
    }

    /** Returns when the circuit state last changed, if known. */
    public String getLastStateTransitionAt() {
        return lastStateTransitionAt;
    }

    /** Returns the low-cardinality outcome for the latest local governance attempt. */
    public String getLastOutcome() {
        return lastOutcome;
    }

    /** Returns when the latest local governance attempt completed or was rejected. */
    public String getLastOutcomeAt() {
        return lastOutcomeAt;
    }
}
