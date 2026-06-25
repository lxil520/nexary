package org.nexary.governance.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.nexary.core.context.CancellationReason;

/** Low-cardinality diagnostic snapshot for one local runtime resource state. */
public final class GovernanceRuntimeSnapshot {
    private final String resourceKey;
    private final String priority;
    private final GovernanceEngine engine;
    private final GovernanceCircuitState circuitState;
    private final int windowCalls;
    private final int windowFailures;
    private final int windowSlowCalls;
    private final int consecutiveFailures;
    private final long totalRejections;
    private final GovernanceRejectionReason lastRejectionReason;
    private final GovernanceBlockReason lastBlockReason;
    private final CancellationReason lastCancellationReason;
    private final Instant openUntil;
    private final int activeConcurrency;
    private final int maxConcurrency;
    private final int maxRequestsPerWindow;
    private final Duration rateLimitWindow;
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
    private final Instant lastStateTransitionAt;
    private final GovernanceCallOutcome lastOutcome;
    private final Instant lastOutcomeAt;

    /** Creates a snapshot with runtime counters and default policy diagnostic values. */
    public GovernanceRuntimeSnapshot(
            String resourceKey,
            String priority,
            GovernanceCircuitState circuitState,
            int windowCalls,
            int windowFailures,
            int windowSlowCalls,
            int consecutiveFailures,
            long totalRejections,
            GovernanceRejectionReason lastRejectionReason,
            Instant openUntil) {
        this(
                resourceKey,
                priority,
                circuitState,
                windowCalls,
                windowFailures,
                windowSlowCalls,
                consecutiveFailures,
                totalRejections,
                lastRejectionReason,
                CancellationReason.NONE,
                openUntil,
                0,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Duration.ofSeconds(1),
                false,
                100,
                Double.POSITIVE_INFINITY,
                Double.POSITIVE_INFINITY,
                null,
                Duration.ofSeconds(60),
                1,
                100,
                Duration.ofSeconds(60),
                Integer.MAX_VALUE,
                null,
                GovernanceCallOutcome.NONE,
                null);
    }

    /** Creates a snapshot with runtime counters, policy diagnostic values, and recent outcome timestamps. */
    public GovernanceRuntimeSnapshot(
            String resourceKey,
            String priority,
            GovernanceCircuitState circuitState,
            int windowCalls,
            int windowFailures,
            int windowSlowCalls,
            int consecutiveFailures,
            long totalRejections,
            GovernanceRejectionReason lastRejectionReason,
            Instant openUntil,
            int activeConcurrency,
            int maxConcurrency,
            int maxRequestsPerWindow,
            Duration rateLimitWindow,
            boolean degraded,
            int minimumRequests,
            double failureRateThreshold,
            double slowCallThreshold,
            Duration slowCallDuration,
            Duration openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            Duration slidingWindowDuration,
            int consecutiveFailureThreshold,
            Instant lastStateTransitionAt,
            GovernanceCallOutcome lastOutcome,
            Instant lastOutcomeAt) {
        this(
                resourceKey,
                priority,
                circuitState,
                windowCalls,
                windowFailures,
                windowSlowCalls,
                consecutiveFailures,
                totalRejections,
                lastRejectionReason,
                CancellationReason.NONE,
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

    /** Creates a snapshot with runtime counters, policy diagnostic values, and cancellation reason. */
    public GovernanceRuntimeSnapshot(
            String resourceKey,
            String priority,
            GovernanceCircuitState circuitState,
            int windowCalls,
            int windowFailures,
            int windowSlowCalls,
            int consecutiveFailures,
            long totalRejections,
            GovernanceRejectionReason lastRejectionReason,
            CancellationReason lastCancellationReason,
            Instant openUntil,
            int activeConcurrency,
            int maxConcurrency,
            int maxRequestsPerWindow,
            Duration rateLimitWindow,
            boolean degraded,
            int minimumRequests,
            double failureRateThreshold,
            double slowCallThreshold,
            Duration slowCallDuration,
            Duration openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            Duration slidingWindowDuration,
            int consecutiveFailureThreshold,
            Instant lastStateTransitionAt,
            GovernanceCallOutcome lastOutcome,
            Instant lastOutcomeAt) {
        this(
                resourceKey,
                priority,
                GovernanceEngine.LOCAL,
                circuitState,
                windowCalls,
                windowFailures,
                windowSlowCalls,
                consecutiveFailures,
                totalRejections,
                lastRejectionReason,
                GovernanceBlockReason.NONE,
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

    /** Creates a snapshot with runtime counters, policy diagnostic values, and engine metadata. */
    public GovernanceRuntimeSnapshot(
            String resourceKey,
            String priority,
            GovernanceEngine engine,
            GovernanceCircuitState circuitState,
            int windowCalls,
            int windowFailures,
            int windowSlowCalls,
            int consecutiveFailures,
            long totalRejections,
            GovernanceRejectionReason lastRejectionReason,
            GovernanceBlockReason lastBlockReason,
            CancellationReason lastCancellationReason,
            Instant openUntil,
            int activeConcurrency,
            int maxConcurrency,
            int maxRequestsPerWindow,
            Duration rateLimitWindow,
            boolean degraded,
            int minimumRequests,
            double failureRateThreshold,
            double slowCallThreshold,
            Duration slowCallDuration,
            Duration openStateDuration,
            int halfOpenMaxCalls,
            int slidingWindowSize,
            Duration slidingWindowDuration,
            int consecutiveFailureThreshold,
            Instant lastStateTransitionAt,
            GovernanceCallOutcome lastOutcome,
            Instant lastOutcomeAt) {
        this.resourceKey = resourceKey == null ? "default:default" : resourceKey;
        this.priority = priority == null ? "normal" : priority;
        this.engine = engine == null ? GovernanceEngine.LOCAL : engine;
        this.circuitState = circuitState == null ? GovernanceCircuitState.CLOSED : circuitState;
        this.windowCalls = Math.max(0, windowCalls);
        this.windowFailures = Math.max(0, windowFailures);
        this.windowSlowCalls = Math.max(0, windowSlowCalls);
        this.consecutiveFailures = Math.max(0, consecutiveFailures);
        this.totalRejections = Math.max(0L, totalRejections);
        this.lastRejectionReason = lastRejectionReason == null ? GovernanceRejectionReason.NONE : lastRejectionReason;
        this.lastBlockReason = lastBlockReason == null ? GovernanceBlockReason.NONE : lastBlockReason;
        this.lastCancellationReason =
                lastCancellationReason == null ? CancellationReason.NONE : lastCancellationReason;
        this.openUntil = openUntil;
        this.activeConcurrency = Math.max(0, activeConcurrency);
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.maxRequestsPerWindow = Math.max(1, maxRequestsPerWindow);
        this.rateLimitWindow = rateLimitWindow == null ? Duration.ofSeconds(1) : rateLimitWindow;
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
        this.lastStateTransitionAt = lastStateTransitionAt;
        this.lastOutcome = lastOutcome == null ? GovernanceCallOutcome.NONE : lastOutcome;
        this.lastOutcomeAt = lastOutcomeAt;
    }

    /** Returns the stable governed resource key. */
    public String resourceKey() {
        return resourceKey;
    }

    /** Returns the request priority bucket for this resource state. */
    public String priority() {
        return priority;
    }

    /** Returns the low-cardinality governance engine label. */
    public GovernanceEngine engine() {
        return engine;
    }

    /** Returns the current circuit state. */
    public GovernanceCircuitState circuitState() {
        return circuitState;
    }

    /** Returns the number of completed calls currently retained in the circuit window. */
    public int windowCalls() {
        return windowCalls;
    }

    /** Returns the number of failed completed calls currently retained in the circuit window. */
    public int windowFailures() {
        return windowFailures;
    }

    /** Returns the number of slow completed calls currently retained in the circuit window. */
    public int windowSlowCalls() {
        return windowSlowCalls;
    }

    /** Returns the current consecutive failed-call count. */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    /** Returns the total number of local governance rejections observed by this state. */
    public long totalRejections() {
        return totalRejections;
    }

    /** Returns the low-cardinality reason for the most recent local governance rejection. */
    public GovernanceRejectionReason lastRejectionReason() {
        return lastRejectionReason;
    }

    /** Returns the low-cardinality block reason reported by the governance engine. */
    public GovernanceBlockReason lastBlockReason() {
        return lastBlockReason;
    }

    /** Returns the low-cardinality reason for the most recent cancellation. */
    public CancellationReason lastCancellationReason() {
        return lastCancellationReason;
    }

    /** Returns when the open circuit may begin half-open probing, if currently known. */
    public Optional<Instant> openUntil() {
        return Optional.ofNullable(openUntil);
    }

    /** Returns the number of currently running calls for this local resource state. */
    public int activeConcurrency() {
        return activeConcurrency;
    }

    /** Returns the configured maximum concurrent calls. */
    public int maxConcurrency() {
        return maxConcurrency;
    }

    /** Returns the configured rate-limit allowance per window. */
    public int maxRequestsPerWindow() {
        return maxRequestsPerWindow;
    }

    /** Returns the configured rate-limit accounting window. */
    public Duration rateLimitWindow() {
        return rateLimitWindow;
    }

    /** Returns whether the current policy routes calls to fallback without running the action. */
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

    /** Returns when the local circuit state last changed, if known. */
    public Optional<Instant> lastStateTransitionAt() {
        return Optional.ofNullable(lastStateTransitionAt);
    }

    /** Returns the low-cardinality outcome for the most recent local governance attempt. */
    public GovernanceCallOutcome lastOutcome() {
        return lastOutcome;
    }

    /** Returns when the most recent local governance attempt completed or was rejected, if known. */
    public Optional<Instant> lastOutcomeAt() {
        return Optional.ofNullable(lastOutcomeAt);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceRuntimeSnapshot)) {
            return false;
        }
        GovernanceRuntimeSnapshot that = (GovernanceRuntimeSnapshot) other;
        return windowCalls == that.windowCalls
                && windowFailures == that.windowFailures
                && windowSlowCalls == that.windowSlowCalls
                && consecutiveFailures == that.consecutiveFailures
                && totalRejections == that.totalRejections
                && activeConcurrency == that.activeConcurrency
                && maxConcurrency == that.maxConcurrency
                && maxRequestsPerWindow == that.maxRequestsPerWindow
                && degraded == that.degraded
                && minimumRequests == that.minimumRequests
                && Double.compare(failureRateThreshold, that.failureRateThreshold) == 0
                && Double.compare(slowCallThreshold, that.slowCallThreshold) == 0
                && halfOpenMaxCalls == that.halfOpenMaxCalls
                && slidingWindowSize == that.slidingWindowSize
                && consecutiveFailureThreshold == that.consecutiveFailureThreshold
                && resourceKey.equals(that.resourceKey)
                && priority.equals(that.priority)
                && engine == that.engine
                && circuitState == that.circuitState
                && lastRejectionReason == that.lastRejectionReason
                && lastBlockReason == that.lastBlockReason
                && lastCancellationReason == that.lastCancellationReason
                && Objects.equals(openUntil, that.openUntil)
                && rateLimitWindow.equals(that.rateLimitWindow)
                && Objects.equals(slowCallDuration, that.slowCallDuration)
                && openStateDuration.equals(that.openStateDuration)
                && slidingWindowDuration.equals(that.slidingWindowDuration)
                && Objects.equals(lastStateTransitionAt, that.lastStateTransitionAt)
                && lastOutcome == that.lastOutcome
                && Objects.equals(lastOutcomeAt, that.lastOutcomeAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                resourceKey,
                priority,
                engine,
                circuitState,
                windowCalls,
                windowFailures,
                windowSlowCalls,
                consecutiveFailures,
                totalRejections,
                lastRejectionReason,
                lastBlockReason,
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

    private static double normalizeThreshold(double threshold) {
        return threshold <= 0.0 || threshold > 100.0 ? Double.POSITIVE_INFINITY : threshold;
    }
}
