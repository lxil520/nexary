package org.nexary.governance.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Local in-process policy for one governed resource. */
public final class GovernancePolicy {
    private final Duration deadline;
    private final int maxRequestsPerWindow;
    private final Duration rateLimitWindow;
    private final int maxConcurrency;
    private final boolean degraded;

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
        this.deadline = deadline == null || deadline.isZero() || deadline.isNegative() ? null : deadline;
        this.maxRequestsPerWindow = maxRequestsPerWindow <= 0 ? Integer.MAX_VALUE : maxRequestsPerWindow;
        this.rateLimitWindow = rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()
                ? Duration.ofSeconds(1)
                : rateLimitWindow;
        this.maxConcurrency = maxConcurrency <= 0 ? Integer.MAX_VALUE : maxConcurrency;
        this.degraded = degraded;
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
                && rateLimitWindow.equals(that.rateLimitWindow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deadline, maxRequestsPerWindow, rateLimitWindow, maxConcurrency, degraded);
    }

    /** Builder for local governance policies. */
    public static final class Builder {
        private Duration deadline;
        private int maxRequestsPerWindow = Integer.MAX_VALUE;
        private Duration rateLimitWindow = Duration.ofSeconds(1);
        private int maxConcurrency = Integer.MAX_VALUE;
        private boolean degraded;

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

        /** Builds an immutable local governance policy. */
        public GovernancePolicy build() {
            return new GovernancePolicy(deadline, maxRequestsPerWindow, rateLimitWindow, maxConcurrency, degraded);
        }
    }
}
