package org.nexary.governance.runtime;

import java.time.Duration;

/** Immutable local settings for instance health detection. */
public final class InstanceHealthSettings {
    private final Duration window;
    private final int minimumCalls;
    private final int suspectWindows;
    private final int recoveryWindows;
    private final Duration slowCallThreshold;
    private final double slowRatioThreshold;
    private final double failureRatioThreshold;
    private final double timeoutRatioThreshold;
    private final double skewFactorThreshold;

    /** Creates settings with default thresholds. */
    public InstanceHealthSettings() {
        this(
                Duration.ofSeconds(60),
                20,
                2,
                2,
                Duration.ofSeconds(2),
                0.60d,
                0.50d,
                0.30d,
                3.0d);
    }

    /** Creates settings for the local instance health detector. */
    public InstanceHealthSettings(
            Duration window,
            int minimumCalls,
            int suspectWindows,
            int recoveryWindows,
            Duration slowCallThreshold,
            double slowRatioThreshold,
            double failureRatioThreshold,
            double timeoutRatioThreshold,
            double skewFactorThreshold) {
        this.window = positive(window, Duration.ofSeconds(60));
        this.minimumCalls = Math.max(1, minimumCalls);
        this.suspectWindows = Math.max(1, suspectWindows);
        this.recoveryWindows = Math.max(1, recoveryWindows);
        this.slowCallThreshold = positive(slowCallThreshold, Duration.ofSeconds(2));
        this.slowRatioThreshold = ratio(slowRatioThreshold, 0.60d);
        this.failureRatioThreshold = ratio(failureRatioThreshold, 0.50d);
        this.timeoutRatioThreshold = ratio(timeoutRatioThreshold, 0.30d);
        this.skewFactorThreshold = Math.max(1.0d, skewFactorThreshold);
    }

    /** Returns the rolling signal window. */
    public Duration window() {
        return window;
    }

    /** Returns the minimum signals needed before anomaly decisions. */
    public int minimumCalls() {
        return minimumCalls;
    }

    /** Returns abnormal windows needed before quarantine-candidate state. */
    public int suspectWindows() {
        return suspectWindows;
    }

    /** Returns healthy windows needed before recovering returns to healthy. */
    public int recoveryWindows() {
        return recoveryWindows;
    }

    /** Returns the slow-call duration threshold used by sample and detectors. */
    public Duration slowCallThreshold() {
        return slowCallThreshold;
    }

    /** Returns the slow-call ratio threshold. */
    public double slowRatioThreshold() {
        return slowRatioThreshold;
    }

    /** Returns the failure ratio threshold. */
    public double failureRatioThreshold() {
        return failureRatioThreshold;
    }

    /** Returns the timeout/reset ratio threshold. */
    public double timeoutRatioThreshold() {
        return timeoutRatioThreshold;
    }

    /** Returns the peer-skew factor threshold. */
    public double skewFactorThreshold() {
        return skewFactorThreshold;
    }

    private static Duration positive(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private static double ratio(double value, double fallback) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            return fallback;
        }
        return value;
    }
}
