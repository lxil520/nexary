package org.nexary.governance.runtime;

import java.time.Duration;
import java.util.Objects;

/** Local in-process policy for one governed resource. */
public final class GovernancePolicy {
    private final int maxRequestsPerWindow;
    private final Duration rateLimitWindow;
    private final int maxConcurrency;
    private final boolean degraded;

    public GovernancePolicy(int maxRequestsPerWindow, Duration rateLimitWindow, int maxConcurrency, boolean degraded) {
        this.maxRequestsPerWindow = maxRequestsPerWindow <= 0 ? Integer.MAX_VALUE : maxRequestsPerWindow;
        this.rateLimitWindow = rateLimitWindow == null || rateLimitWindow.isZero() || rateLimitWindow.isNegative()
                ? Duration.ofSeconds(1)
                : rateLimitWindow;
        this.maxConcurrency = maxConcurrency <= 0 ? Integer.MAX_VALUE : maxConcurrency;
        this.degraded = degraded;
    }

    /** Creates a policy that does not reject calls. */
    public static GovernancePolicy allowAll() {
        return builder().build();
    }

    /** Creates a policy builder. */
    public static Builder builder() {
        return new Builder();
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
        return maxRequestsPerWindow == that.maxRequestsPerWindow
                && maxConcurrency == that.maxConcurrency
                && degraded == that.degraded
                && rateLimitWindow.equals(that.rateLimitWindow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxRequestsPerWindow, rateLimitWindow, maxConcurrency, degraded);
    }

    /** Builder for local governance policies. */
    public static final class Builder {
        private int maxRequestsPerWindow = Integer.MAX_VALUE;
        private Duration rateLimitWindow = Duration.ofSeconds(1);
        private int maxConcurrency = Integer.MAX_VALUE;
        private boolean degraded;

        public Builder maxRequestsPerWindow(int maxRequestsPerWindow) {
            this.maxRequestsPerWindow = maxRequestsPerWindow;
            return this;
        }

        public Builder rateLimitWindow(Duration rateLimitWindow) {
            this.rateLimitWindow = rateLimitWindow;
            return this;
        }

        public Builder maxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder degraded(boolean degraded) {
            this.degraded = degraded;
            return this;
        }

        public GovernancePolicy build() {
            return new GovernancePolicy(maxRequestsPerWindow, rateLimitWindow, maxConcurrency, degraded);
        }
    }
}
