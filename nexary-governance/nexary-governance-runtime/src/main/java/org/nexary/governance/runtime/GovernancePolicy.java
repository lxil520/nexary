package org.nexary.governance.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Local in-process policy for one governed resource. */
public final class GovernancePolicy {
    private static final int DISABLED_COUNT_THRESHOLD = Integer.MAX_VALUE;
    private static final double DISABLED_PERCENTAGE_THRESHOLD = Double.POSITIVE_INFINITY;
    private static final int DEFAULT_MINIMUM_REQUESTS = 100;

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

    /**
     * Creates an immutable local governance policy.
     *
     * <p>Non-positive numeric limits are normalized to unlimited. Null, zero, or
     * negative durations disable the deadline or reset the rate-limit window to
     * the default one-second window.</p>
     */
    public GovernancePolicy(
            Duration deadline,
            int maxRequestsPerWindow,
            Duration rateLimitWindow,
            int maxConcurrency,
            boolean degraded) {
        this(
                deadline,
                maxRequestsPerWindow,
                rateLimitWindow,
                maxConcurrency,
                degraded,
                DEFAULT_MINIMUM_REQUESTS,
                DISABLED_PERCENTAGE_THRESHOLD,
                DISABLED_PERCENTAGE_THRESHOLD,
                null,
                Duration.ofSeconds(60),
                1,
                100,
                Duration.ofSeconds(60),
                DISABLED_COUNT_THRESHOLD);
    }

    private GovernancePolicy(
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
        this.maxRequestsPerWindow = maxRequestsPerWindow <= 0 ? Integer.MAX_VALUE : maxRequestsPerWindow;
        this.rateLimitWindow = rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()
                ? Duration.ofSeconds(1)
                : rateLimitWindow;
        this.maxConcurrency = maxConcurrency <= 0 ? Integer.MAX_VALUE : maxConcurrency;
        this.degraded = degraded;
        this.minimumRequests = minimumRequests <= 0 ? DEFAULT_MINIMUM_REQUESTS : minimumRequests;
        this.failureRateThreshold = normalizePercentageThreshold(failureRateThreshold);
        this.slowCallThreshold = normalizePercentageThreshold(slowCallThreshold);
        this.slowCallDuration = slowCallDuration == null || slowCallDuration.isZero() || slowCallDuration.isNegative()
                ? null
                : slowCallDuration;
        this.openStateDuration = openStateDuration == null || openStateDuration.isZero() || openStateDuration.isNegative()
                ? Duration.ofSeconds(60)
                : openStateDuration;
        this.halfOpenMaxCalls = halfOpenMaxCalls <= 0 ? 1 : halfOpenMaxCalls;
        this.slidingWindowSize = slidingWindowSize <= 0 ? 100 : slidingWindowSize;
        this.slidingWindowDuration = slidingWindowDuration == null
                        || slidingWindowDuration.isZero()
                        || slidingWindowDuration.isNegative()
                ? Duration.ofSeconds(60)
                : slidingWindowDuration;
        this.consecutiveFailureThreshold = consecutiveFailureThreshold <= 0
                ? DISABLED_COUNT_THRESHOLD
                : consecutiveFailureThreshold;
    }

    /** Creates an immutable local governance policy without a policy-level deadline. */
    public GovernancePolicy(int maxRequestsPerWindow, Duration rateLimitWindow, int maxConcurrency, boolean degraded) {
        this(null, maxRequestsPerWindow, rateLimitWindow, maxConcurrency, degraded);
    }

    /** Creates a policy that does not reject calls. */
    public static GovernancePolicy allowAll() {
        return builder().build();
    }

    /** Creates a policy builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the optional maximum time allowed for the protected action. */
    public Optional<Duration> deadline() {
        return Optional.ofNullable(deadline);
    }

    /** Returns the number of calls allowed during the rate-limit window. */
    public int maxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    /** Returns the rate-limit window. */
    public Duration rateLimitWindow() {
        return rateLimitWindow;
    }

    /** Returns the maximum number of concurrent calls. */
    public int maxConcurrency() {
        return maxConcurrency;
    }

    /** Returns true when calls should use fallback without running the main action. */
    public boolean degraded() {
        return degraded;
    }

    /** Returns the minimum completed calls required before window rate thresholds can open the circuit. */
    public int minimumRequests() {
        return minimumRequests;
    }

    /** Returns the failed-call percentage that opens the circuit, or infinity when disabled. */
    public double failureRateThreshold() {
        return failureRateThreshold;
    }

    /** Returns the slow-call percentage that opens the circuit, or infinity when disabled. */
    public double slowCallThreshold() {
        return slowCallThreshold;
    }

    /** Returns the optional duration after which a completed call is counted as slow. */
    public Optional<Duration> slowCallDuration() {
        return Optional.ofNullable(slowCallDuration);
    }

    /** Returns how long the circuit remains open before allowing half-open probes. */
    public Duration openStateDuration() {
        return openStateDuration;
    }

    /** Returns the maximum concurrent calls allowed while the circuit is half-open. */
    public int halfOpenMaxCalls() {
        return halfOpenMaxCalls;
    }

    /** Returns the maximum number of recent completed calls retained for circuit decisions. */
    public int slidingWindowSize() {
        return slidingWindowSize;
    }

    /** Returns the maximum age of completed calls retained for circuit decisions. */
    public Duration slidingWindowDuration() {
        return slidingWindowDuration;
    }

    /** Returns the consecutive failed-call count that opens the circuit, or Integer.MAX_VALUE when disabled. */
    public int consecutiveFailureThreshold() {
        return consecutiveFailureThreshold;
    }

    /** Returns true when this policy can open the circuit from runtime call records. */
    public boolean circuitBreakerEnabled() {
        return consecutiveFailureThreshold != DISABLED_COUNT_THRESHOLD
                || failureRateThreshold != DISABLED_PERCENTAGE_THRESHOLD
                || slowCallThreshold != DISABLED_PERCENTAGE_THRESHOLD;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernancePolicy)) {
            return false;
        }
        GovernancePolicy that = (GovernancePolicy) other;
        return Objects.equals(deadline, that.deadline)
                && maxRequestsPerWindow == that.maxRequestsPerWindow
                && maxConcurrency == that.maxConcurrency
                && degraded == that.degraded
                && minimumRequests == that.minimumRequests
                && Double.compare(failureRateThreshold, that.failureRateThreshold) == 0
                && Double.compare(slowCallThreshold, that.slowCallThreshold) == 0
                && halfOpenMaxCalls == that.halfOpenMaxCalls
                && slidingWindowSize == that.slidingWindowSize
                && consecutiveFailureThreshold == that.consecutiveFailureThreshold
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

    private static double normalizePercentageThreshold(double threshold) {
        return threshold <= 0.0 || threshold > 100.0 ? DISABLED_PERCENTAGE_THRESHOLD : threshold;
    }

    /** Builder for local governance policies. */
    public static final class Builder {
        private Duration deadline;
        private int maxRequestsPerWindow = Integer.MAX_VALUE;
        private Duration rateLimitWindow = Duration.ofSeconds(1);
        private int maxConcurrency = Integer.MAX_VALUE;
        private boolean degraded;
        private int minimumRequests = DEFAULT_MINIMUM_REQUESTS;
        private double failureRateThreshold = DISABLED_PERCENTAGE_THRESHOLD;
        private double slowCallThreshold = DISABLED_PERCENTAGE_THRESHOLD;
        private Duration slowCallDuration;
        private Duration openStateDuration = Duration.ofSeconds(60);
        private int halfOpenMaxCalls = 1;
        private int slidingWindowSize = 100;
        private Duration slidingWindowDuration = Duration.ofSeconds(60);
        private int consecutiveFailureThreshold = DISABLED_COUNT_THRESHOLD;

        /** Sets the maximum time allowed for the protected action. */
        public Builder deadline(Duration deadline) {
            this.deadline = deadline;
            return this;
        }

        /** Sets the number of calls allowed during each rate-limit window. */
        public Builder maxRequestsPerWindow(int maxRequestsPerWindow) {
            this.maxRequestsPerWindow = maxRequestsPerWindow;
            return this;
        }

        /** Sets the rate-limit accounting window. */
        public Builder rateLimitWindow(Duration rateLimitWindow) {
            this.rateLimitWindow = rateLimitWindow;
            return this;
        }

        /** Sets the maximum number of concurrent calls. */
        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        /** Sets whether calls should use fallback without running the main action. */
        public Builder degraded(boolean degraded) {
            this.degraded = degraded;
            return this;
        }

        /** Sets the minimum completed calls required before window rate thresholds can open the circuit. */
        public Builder minimumRequests(int minimumRequests) {
            this.minimumRequests = minimumRequests;
            return this;
        }

        /** Sets the failed-call percentage that opens the circuit. */
        public Builder failureRateThreshold(double failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
            return this;
        }

        /** Sets the slow-call percentage that opens the circuit. */
        public Builder slowCallThreshold(double slowCallThreshold) {
            this.slowCallThreshold = slowCallThreshold;
            return this;
        }

        /** Sets the completed-call duration after which a call counts as slow. */
        public Builder slowCallDuration(Duration slowCallDuration) {
            this.slowCallDuration = slowCallDuration;
            return this;
        }

        /** Sets how long the circuit remains open before allowing half-open probes. */
        public Builder openStateDuration(Duration openStateDuration) {
            this.openStateDuration = openStateDuration;
            return this;
        }

        /** Sets the maximum concurrent calls allowed while the circuit is half-open. */
        public Builder halfOpenMaxCalls(int halfOpenMaxCalls) {
            this.halfOpenMaxCalls = halfOpenMaxCalls;
            return this;
        }

        /** Sets the maximum number of recent completed calls retained for circuit decisions. */
        public Builder slidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
            return this;
        }

        /** Sets the maximum age of completed calls retained for circuit decisions. */
        public Builder slidingWindowDuration(Duration slidingWindowDuration) {
            this.slidingWindowDuration = slidingWindowDuration;
            return this;
        }

        /** Sets the consecutive failed-call count that opens the circuit. */
        public Builder consecutiveFailureThreshold(int consecutiveFailureThreshold) {
            this.consecutiveFailureThreshold = consecutiveFailureThreshold;
            return this;
        }

        /** Builds an immutable local governance policy. */
        public GovernancePolicy build() {
            return new GovernancePolicy(
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
    }
}
