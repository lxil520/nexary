package org.nexary.console.api;

/**
 * Read-only policy fields shown by the Nexary console for one governed resource.
 */
public final class ConsolePolicySnapshot {
    private final String deadline;
    private final int maxRequestsPerWindow;
    private final String rateLimitWindow;
    private final int maxConcurrency;
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

    /**
     * Creates a policy snapshot with bounded configuration fields.
     */
    public ConsolePolicySnapshot(
            String deadline,
            int maxRequestsPerWindow,
            String rateLimitWindow,
            int maxConcurrency,
            boolean degraded,
            int minimumRequests,
            Double failureRateThreshold,
            Double slowCallThreshold,
            String slowCallDuration,
            String openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            String slidingWindowDuration,
            int consecutiveFailureThreshold) {
        this.deadline = deadline;
        this.maxRequestsPerWindow = maxRequestsPerWindow;
        this.rateLimitWindow = rateLimitWindow;
        this.maxConcurrency = maxConcurrency;
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
    }

    /** Returns the optional deadline as an ISO-8601 duration. */
    public String getDeadline() {
        return deadline;
    }

    /** Returns the configured rate-limit allowance per accounting window. */
    public int getMaxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    /** Returns the rate-limit accounting window as an ISO-8601 duration. */
    public String getRateLimitWindow() {
        return rateLimitWindow;
    }

    /** Returns the configured maximum concurrent calls. */
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    /** Returns whether the policy routes calls to fallback without running the action. */
    public boolean isDegraded() {
        return degraded;
    }

    /** Returns the minimum completed calls before circuit percentage checks run. */
    public int getMinimumRequests() {
        return minimumRequests;
    }

    /** Returns the failure-rate threshold percentage, or null when disabled. */
    public Double getFailureRateThreshold() {
        return failureRateThreshold;
    }

    /** Returns the slow-call-rate threshold percentage, or null when disabled. */
    public Double getSlowCallThreshold() {
        return slowCallThreshold;
    }

    /** Returns the duration after which a completed call counts as slow, if enabled. */
    public String getSlowCallDuration() {
        return slowCallDuration;
    }

    /** Returns how long an open circuit stays open before half-open probing. */
    public String getOpenStateDuration() {
        return openStateDuration;
    }

    /** Returns the maximum concurrent half-open probe calls. */
    public int getHalfOpenMaxCalls() {
        return halfOpenMaxCalls;
    }

    /** Returns the maximum number of completed calls retained in the sliding window. */
    public int getSlidingWindowSize() {
        return slidingWindowSize;
    }

    /** Returns the maximum age of completed calls retained in the sliding window. */
    public String getSlidingWindowDuration() {
        return slidingWindowDuration;
    }

    /** Returns the consecutive failed-call count that opens the circuit. */
    public int getConsecutiveFailureThreshold() {
        return consecutiveFailureThreshold;
    }
}
