package org.nexary.governance.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Low-cardinality snapshot of the policy last applied to a governed resource. */
public final class GovernancePolicySnapshot {
    private final Duration deadline;
    private final int maxRequestsPerWindow;
    private final Duration rateLimitWindow;
    private final int maxConcurrency;
    private final boolean degraded;
    private final int minimumRequests;
    private final double failureRateThreshold;
    private final double slowCallThreshold;
    private final Duration slowCallDuration;
    private final Duration openStateDuration;
    private final int halfOpenMaxCalls;
    private final int slidingWindowSize;
    private final Duration slidingWindowDuration;
    private final int consecutiveFailureThreshold;

    /** Creates a policy snapshot from a runtime policy. */
    public static GovernancePolicySnapshot from(GovernancePolicy policy) {
        GovernancePolicy safePolicy = policy == null ? GovernancePolicy.allowAll() : policy;
        return new GovernancePolicySnapshot(
                safePolicy.deadline().orElse(null),
                safePolicy.maxRequestsPerWindow(),
                safePolicy.rateLimitWindow(),
                safePolicy.maxConcurrency(),
                safePolicy.degraded(),
                safePolicy.minimumRequests(),
                safePolicy.failureRateThreshold(),
                safePolicy.slowCallThreshold(),
                safePolicy.slowCallDuration().orElse(null),
                safePolicy.openStateDuration(),
                safePolicy.halfOpenMaxCalls(),
                safePolicy.slidingWindowSize(),
                safePolicy.slidingWindowDuration(),
                safePolicy.consecutiveFailureThreshold());
    }

    /** Creates a policy snapshot with bounded policy fields. */
    public GovernancePolicySnapshot(
            int maxRequestsPerWindow,
            Duration rateLimitWindow,
            int maxConcurrency,
            boolean degraded,
            int minimumRequests,
            double failureRateThreshold,
            double slowCallThreshold,
            Duration slowCallDuration,
            Duration openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            Duration slidingWindowDuration,
            int consecutiveFailureThreshold) {
        this(
                null,
                maxRequestsPerWindow,
                rateLimitWindow,
                maxConcurrency,
                degraded,
                minimumRequests,
                failureRateThreshold,
                slowCallThreshold,
                slowCallDuration,
                openStateDuration,
                halfOpenMaxCalls,
                slidingWindowSize,
                slidingWindowDuration,
                consecutiveFailureThreshold);
    }

    /** Creates a policy snapshot with bounded policy fields including the optional deadline. */
    public GovernancePolicySnapshot(
            Duration deadline,
            int maxRequestsPerWindow,
            Duration rateLimitWindow,
            int maxConcurrency,
            boolean degraded,
            int minimumRequests,
            double failureRateThreshold,
            double slowCallThreshold,
            Duration slowCallDuration,
            Duration openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            Duration slidingWindowDuration,
            int consecutiveFailureThreshold) {
        this.deadline = deadline == null || deadline.isZero() || deadline.isNegative() ? null : deadline;
        this.maxRequestsPerWindow = Math.max(1, maxRequestsPerWindow);
        this.rateLimitWindow = rateLimitWindow == null ? Duration.ofSeconds(1) : rateLimitWindow;
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.degraded = degraded;
        this.minimumRequests = Math.max(1, minimumRequests);
        this.failureRateThreshold = normalizeThreshold(failureRateThreshold);
        this.slowCallThreshold = normalizeThreshold(slowCallThreshold);
        this.slowCallDuration = slowCallDuration;
        this.openStateDuration = openStateDuration == null ? Duration.ofSeconds(60) : openStateDuration;
        this.halfOpenMaxCalls = Math.max(1, halfOpenMaxCalls);
        this.slidingWindowSize = Math.max(1, slidingWindowSize);
        this.slidingWindowDuration = slidingWindowDuration == null ? Duration.ofSeconds(60) : slidingWindowDuration;
        this.consecutiveFailureThreshold = Math.max(1, consecutiveFailureThreshold);
    }

    /** Returns the optional policy-level deadline. */
    public Optional<Duration> deadline() {
        return Optional.ofNullable(deadline);
    }

    /** Returns the configured rate-limit allowance per window. */
    public int maxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    /** Returns the configured rate-limit accounting window. */
    public Duration rateLimitWindow() {
        return rateLimitWindow;
    }

    /** Returns the configured maximum concurrent calls. */
    public int maxConcurrency() {
        return maxConcurrency;
    }

    /** Returns whether the policy routes calls to fallback without running the action. */
    public boolean degraded() {
        return degraded;
    }

    /** Returns the configured minimum completed calls before circuit percentage checks run. */
    public int minimumRequests() {
        return minimumRequests;
    }

    /** Returns the configured failure-rate threshold percentage, or infinity when disabled. */
    public double failureRateThreshold() {
        return failureRateThreshold;
    }

    /** Returns the configured slow-call-rate threshold percentage, or infinity when disabled. */
    public double slowCallThreshold() {
        return slowCallThreshold;
    }

    /** Returns the configured duration after which a completed call counts as slow, if enabled. */
    public Optional<Duration> slowCallDuration() {
        return Optional.ofNullable(slowCallDuration);
    }

    /** Returns how long an open circuit stays open before half-open probing. */
    public Duration openStateDuration() {
        return openStateDuration;
    }

    /** Returns the configured maximum concurrent half-open probe calls. */
    public int halfOpenMaxCalls() {
        return halfOpenMaxCalls;
    }

    /** Returns the configured maximum number of completed calls retained in the sliding window. */
    public int slidingWindowSize() {
        return slidingWindowSize;
    }

    /** Returns the configured maximum age of completed calls retained in the sliding window. */
    public Duration slidingWindowDuration() {
        return slidingWindowDuration;
    }

    /** Returns the configured consecutive failed-call count that opens the circuit. */
    public int consecutiveFailureThreshold() {
        return consecutiveFailureThreshold;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernancePolicySnapshot)) {
            return false;
        }
        GovernancePolicySnapshot that = (GovernancePolicySnapshot) other;
        return maxRequestsPerWindow == that.maxRequestsPerWindow
                && maxConcurrency == that.maxConcurrency
                && degraded == that.degraded
                && minimumRequests == that.minimumRequests
                && Double.compare(failureRateThreshold, that.failureRateThreshold) == 0
                && Double.compare(slowCallThreshold, that.slowCallThreshold) == 0
                && halfOpenMaxCalls == that.halfOpenMaxCalls
                && slidingWindowSize == that.slidingWindowSize
                && consecutiveFailureThreshold == that.consecutiveFailureThreshold
                && Objects.equals(deadline, that.deadline)
                && rateLimitWindow.equals(that.rateLimitWindow)
                && Objects.equals(slowCallDuration, that.slowCallDuration)
                && openStateDuration.equals(that.openStateDuration)
                && slidingWindowDuration.equals(that.slidingWindowDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                deadline,
                maxRequestsPerWindow,
                rateLimitWindow,
                maxConcurrency,
                degraded,
                minimumRequests,
                failureRateThreshold,
                slowCallThreshold,
                slowCallDuration,
                openStateDuration,
                halfOpenMaxCalls,
                slidingWindowSize,
                slidingWindowDuration,
                consecutiveFailureThreshold);
    }

    private static double normalizeThreshold(double threshold) {
        return threshold <= 0.0 || threshold > 100.0 ? Double.POSITIVE_INFINITY : threshold;
    }
}
