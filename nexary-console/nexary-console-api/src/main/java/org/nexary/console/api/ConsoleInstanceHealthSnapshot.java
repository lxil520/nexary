package org.nexary.console.api;

/**
 * Read-only console view of one low-cardinality downstream instance health snapshot.
 */
public final class ConsoleInstanceHealthSnapshot {
    private final String resourceKey;
    private final String serviceKey;
    private final String instanceKey;
    private final String zone;
    private final String state;
    private final String quarantineReason;
    private final String recoveryAdvice;
    private final int windowCalls;
    private final int failureCount;
    private final int slowCallCount;
    private final int timeoutCount;
    private final int resetCount;
    private final int serverErrorCount;
    private final double failureRatio;
    private final double slowRatio;
    private final double timeoutRatio;
    private final double skewFactor;
    private final String lastSignalAt;
    private final String lastChangedAt;

    /** Creates an instance health snapshot from bounded diagnostic fields. */
    public ConsoleInstanceHealthSnapshot(
            String resourceKey,
            String serviceKey,
            String instanceKey,
            String zone,
            String state,
            String quarantineReason,
            String recoveryAdvice,
            int windowCalls,
            int failureCount,
            int slowCallCount,
            int timeoutCount,
            int resetCount,
            int serverErrorCount,
            double failureRatio,
            double slowRatio,
            double timeoutRatio,
            double skewFactor,
            String lastSignalAt,
            String lastChangedAt) {
        this.resourceKey = resourceKey;
        this.serviceKey = serviceKey;
        this.instanceKey = instanceKey;
        this.zone = zone;
        this.state = state;
        this.quarantineReason = quarantineReason;
        this.recoveryAdvice = recoveryAdvice;
        this.windowCalls = windowCalls;
        this.failureCount = failureCount;
        this.slowCallCount = slowCallCount;
        this.timeoutCount = timeoutCount;
        this.resetCount = resetCount;
        this.serverErrorCount = serverErrorCount;
        this.failureRatio = failureRatio;
        this.slowRatio = slowRatio;
        this.timeoutRatio = timeoutRatio;
        this.skewFactor = skewFactor;
        this.lastSignalAt = lastSignalAt;
        this.lastChangedAt = lastChangedAt;
    }

    /** Returns the governed resource key. */
    public String getResourceKey() {
        return resourceKey;
    }

    /** Returns the bounded service key. */
    public String getServiceKey() {
        return serviceKey;
    }

    /** Returns the stable instance alias or fingerprint. */
    public String getInstanceKey() {
        return instanceKey;
    }

    /** Returns the bounded zone label. */
    public String getZone() {
        return zone;
    }

    /** Returns the local health state. */
    public String getState() {
        return state;
    }

    /** Returns the top low-cardinality quarantine reason. */
    public String getQuarantineReason() {
        return quarantineReason;
    }

    /** Returns the bounded recovery advice. */
    public String getRecoveryAdvice() {
        return recoveryAdvice;
    }

    /** Returns retained calls in the current window. */
    public int getWindowCalls() {
        return windowCalls;
    }

    /** Returns retained failure signals. */
    public int getFailureCount() {
        return failureCount;
    }

    /** Returns retained slow-call signals. */
    public int getSlowCallCount() {
        return slowCallCount;
    }

    /** Returns retained timeout signals. */
    public int getTimeoutCount() {
        return timeoutCount;
    }

    /** Returns retained reset signals. */
    public int getResetCount() {
        return resetCount;
    }

    /** Returns retained server-error signals. */
    public int getServerErrorCount() {
        return serverErrorCount;
    }

    /** Returns retained failure ratio. */
    public double getFailureRatio() {
        return failureRatio;
    }

    /** Returns retained slow-call ratio. */
    public double getSlowRatio() {
        return slowRatio;
    }

    /** Returns retained timeout/reset ratio. */
    public double getTimeoutRatio() {
        return timeoutRatio;
    }

    /** Returns peer skew factor. */
    public double getSkewFactor() {
        return skewFactor;
    }

    /** Returns when the latest signal was recorded. */
    public String getLastSignalAt() {
        return lastSignalAt;
    }

    /** Returns when the state last changed. */
    public String getLastChangedAt() {
        return lastChangedAt;
    }
}
