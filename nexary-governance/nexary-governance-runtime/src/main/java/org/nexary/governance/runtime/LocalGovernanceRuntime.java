package org.nexary.governance.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.nexary.core.fault.FaultSignal;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceObservationEvents;
import org.nexary.core.observation.NexaryObservationPublisher;

/** Default local governance runtime. */
public final class LocalGovernanceRuntime implements GovernanceRuntime {
    private final GovernancePolicyRegistry policyRegistry;
    private final NexaryObservationPublisher observationPublisher;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public LocalGovernanceRuntime(GovernancePolicyRegistry policyRegistry, NexaryObservationPublisher observationPublisher) {
        this.policyRegistry = policyRegistry == null
                ? LocalGovernancePolicyRegistry.builder().build()
                : policyRegistry;
        this.observationPublisher = observationPublisher == null
                ? NexaryObservationPublisher.noop()
                : observationPublisher;
    }

    /** Returns low-cardinality diagnostic snapshots for current local resource states. */
    public List<GovernanceRuntimeSnapshot> snapshots() {
        List<GovernanceRuntimeSnapshot> snapshots = new ArrayList<>();
        for (State state : states.values()) {
            snapshots.add(state.snapshot());
        }
        snapshots.sort(Comparator
                .comparing(GovernanceRuntimeSnapshot::resourceKey)
                .thenComparing(GovernanceRuntimeSnapshot::priority));
        return snapshots;
    }

    @Override
    public <T> T execute(GovernanceContext context, Callable<T> action) throws Exception {
        return execute(context, action, null);
    }

    @Override
    public <T> T execute(GovernanceContext context, Callable<T> action, Callable<T> fallback) throws Exception {
        Objects.requireNonNull(action, "action");
        GovernanceContext safeContext = context == null ? GovernanceContext.builder().build() : context;
        GovernancePolicy policy = policyRegistry.policyFor(safeContext);
        if (policy == null) {
            policy = GovernancePolicy.allowAll();
        }
        Instant startedAt = Instant.now();
        GovernanceContext effectiveContext = withPolicyDeadline(safeContext, policy, startedAt);
        State state = stateFor(effectiveContext, policy);
        state.touchPolicy(policy);
        GovernanceDecision rejected = rejectBeforeAcquire(effectiveContext, state, policy);
        if (rejected != null) {
            publishRejection(effectiveContext, rejected, startedAt);
            return fallbackOrThrow(fallback, rejected);
        }
        CircuitPermit circuitPermit = state.tryEnterCircuit(policy);
        if (!circuitPermit.allowed()) {
            GovernanceDecision decision = circuitOpenDecision(circuitPermit.rejectionReason());
            observationPublisher.publish(GovernanceObservationEvents.circuitOpen(
                    effectiveContext.resource(),
                    effectiveContext.trafficTag(),
                    circuitPermit.rejectionReason() == GovernanceRejectionReason.HALF_OPEN_LIMITED
                            ? "half_open_limited"
                            : "circuit_open",
                    startedAt,
                    Instant.now()));
            publishRetryStopped(effectiveContext, decision, startedAt);
            return fallbackOrThrow(fallback, decision);
        }
        if (!state.allowRate(policy)) {
            state.cancelCircuitPermit(circuitPermit);
            state.recordRejection(GovernanceRejectionReason.RATE_LIMITED);
            GovernanceDecision decision = GovernanceDecision.rejected(
                    GovernanceDecision.Decision.RATE_LIMITED,
                    new FaultSignal(FaultSignal.FaultType.RATE_LIMITED, "governance", "rate limited", Instant.now(), true));
            publishRejection(effectiveContext, decision, startedAt);
            return fallbackOrThrow(fallback, decision);
        }
        if (!state.tryAcquire()) {
            state.cancelCircuitPermit(circuitPermit);
            state.recordRejection(GovernanceRejectionReason.CONCURRENCY_LIMITED);
            GovernanceDecision decision = GovernanceDecision.rejected(
                    GovernanceDecision.Decision.CONCURRENCY_LIMITED,
                    new FaultSignal(FaultSignal.FaultType.REJECTED, "governance", "concurrency limited", Instant.now(), true));
            observationPublisher.publish(GovernanceObservationEvents.bulkheadRejected(
                    effectiveContext.resource(), effectiveContext.trafficTag(), startedAt, Instant.now()));
            publishRetryStopped(effectiveContext, decision, startedAt);
            return fallbackOrThrow(fallback, decision);
        }
        long actionStartedNanos = System.nanoTime();
        Throwable failure = null;
        try {
            return GovernanceContext.callWithContext(effectiveContext, action);
        } catch (Throwable error) {
            failure = error;
            return rethrow(error);
        } finally {
            state.recordCall(policy, actionStartedNanos, System.nanoTime(), failure != null, circuitPermit);
            state.release();
        }
    }

    private GovernanceContext withPolicyDeadline(GovernanceContext context, GovernancePolicy policy, Instant startedAt) {
        if (!policy.deadline().isPresent()) {
            return context;
        }
        Instant policyDeadline = startedAt.plus(policy.deadline().get());
        Instant effectiveDeadline = context.deadline()
                .map(existing -> existing.isBefore(policyDeadline) ? existing : policyDeadline)
                .orElse(policyDeadline);
        if (context.deadline().isPresent() && context.deadline().get().equals(effectiveDeadline)) {
            return context;
        }
        GovernanceContext.Builder builder = GovernanceContext.builder()
                .resource(context.resource())
                .trafficTag(context.trafficTag())
                .priority(context.priority())
                .deadline(effectiveDeadline);
        context.attributes().forEach(builder::attribute);
        return builder.build();
    }

    private GovernanceDecision rejectBeforeAcquire(GovernanceContext context, State state, GovernancePolicy policy) {
        if (context.deadlineExpired()) {
            state.recordRejection(GovernanceRejectionReason.DEADLINE_EXPIRED);
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.DEADLINE_EXPIRED,
                    new FaultSignal(FaultSignal.FaultType.TIMEOUT, "governance", "deadline expired", Instant.now(), false));
        }
        if (policy.degraded()) {
            state.recordRejection(GovernanceRejectionReason.DEGRADED);
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.DEGRADED,
                    new FaultSignal(FaultSignal.FaultType.DEGRADED, "governance", "degraded", Instant.now(), false));
        }
        return null;
    }

    private GovernanceDecision circuitOpenDecision(GovernanceRejectionReason reason) {
        String message = reason == GovernanceRejectionReason.HALF_OPEN_LIMITED
                ? "half-open probes limited"
                : "circuit open";
        return GovernanceDecision.rejected(
                GovernanceDecision.Decision.CIRCUIT_OPEN,
                new FaultSignal(FaultSignal.FaultType.REJECTED, "governance", message, Instant.now(), true));
    }

    private void publishRejection(GovernanceContext context, GovernanceDecision decision, Instant startedAt) {
        Instant endedAt = Instant.now();
        if (decision.decision() == GovernanceDecision.Decision.DEADLINE_EXPIRED) {
            observationPublisher.publish(GovernanceObservationEvents.deadlineExceeded(
                    context.resource(), context.trafficTag(), startedAt, endedAt));
        } else if (decision.decision() == GovernanceDecision.Decision.DEGRADED) {
            observationPublisher.publish(GovernanceObservationEvents.degraded(
                    context.resource(), context.trafficTag(), startedAt, endedAt));
        } else if (decision.decision() == GovernanceDecision.Decision.RATE_LIMITED) {
            observationPublisher.publish(GovernanceObservationEvents.rateLimited(
                    context.resource(), context.trafficTag(), startedAt, endedAt));
        }
        publishRetryStopped(context, decision, startedAt);
    }

    private void publishRetryStopped(GovernanceContext context, GovernanceDecision decision, Instant startedAt) {
        if (decision.retrySignal() != null) {
            observationPublisher.publish(GovernanceObservationEvents.retryStopped(
                    context.resource(), context.trafficTag(), decision.retrySignal(), startedAt, Instant.now()));
        }
    }

    private <T> T fallbackOrThrow(Callable<T> fallback, GovernanceDecision decision) throws Exception {
        if (fallback != null) {
            return fallback.call();
        }
        throw new GovernanceRejectedException(decision);
    }

    private static <T> T rethrow(Throwable error) throws Exception {
        if (error instanceof Exception) {
            throw (Exception) error;
        }
        if (error instanceof Error) {
            throw (Error) error;
        }
        throw new RuntimeException(error);
    }

    private State stateFor(GovernanceContext context, GovernancePolicy policy) {
        String key = context.resource().key() + ":" + context.priority().name().toLowerCase(Locale.ROOT);
        return states.compute(key, (ignored, current) -> {
            String priority = context.priority().name().toLowerCase(Locale.ROOT);
            if (current != null && current.maxConcurrency == policy.maxConcurrency()) {
                return current;
            }
            return new State(context.resource().key(), priority, policy.maxConcurrency());
        });
    }

    private static final class State {
        private final String resourceKey;
        private final String priority;
        private final int maxConcurrency;
        private final Semaphore semaphore;
        private long windowStartedNanos = System.nanoTime();
        private int used;
        private final ArrayDeque<CallRecord> calls = new ArrayDeque<>();
        private GovernanceCircuitState circuitState = GovernanceCircuitState.CLOSED;
        private long openedAtNanos;
        private Instant openUntil;
        private int halfOpenInFlight;
        private int halfOpenSuccesses;
        private int consecutiveFailures;
        private long totalRejections;
        private GovernanceRejectionReason lastRejectionReason = GovernanceRejectionReason.NONE;
        private int lastSlidingWindowSize = 100;
        private long lastSlidingWindowNanos = Duration.ofSeconds(60).toNanos();

        private State(String resourceKey, String priority, int maxConcurrency) {
            this.resourceKey = resourceKey;
            this.priority = priority;
            this.maxConcurrency = maxConcurrency;
            this.semaphore = new Semaphore(maxConcurrency);
        }

        private synchronized void touchPolicy(GovernancePolicy policy) {
            lastSlidingWindowSize = policy.slidingWindowSize();
            lastSlidingWindowNanos = policy.slidingWindowDuration().toNanos();
            pruneCalls(System.nanoTime());
        }

        private synchronized CircuitPermit tryEnterCircuit(GovernancePolicy policy) {
            if (!policy.circuitBreakerEnabled()) {
                circuitState = GovernanceCircuitState.CLOSED;
                halfOpenInFlight = 0;
                halfOpenSuccesses = 0;
                openUntil = null;
                return CircuitPermit.allowed(false);
            }
            long now = System.nanoTime();
            pruneCalls(now);
            if (circuitState == GovernanceCircuitState.OPEN
                    && now - openedAtNanos >= policy.openStateDuration().toNanos()) {
                circuitState = GovernanceCircuitState.HALF_OPEN;
                halfOpenInFlight = 0;
                halfOpenSuccesses = 0;
            }
            if (circuitState == GovernanceCircuitState.OPEN) {
                recordRejection(GovernanceRejectionReason.CIRCUIT_OPEN);
                return CircuitPermit.rejected(GovernanceRejectionReason.CIRCUIT_OPEN);
            }
            if (circuitState == GovernanceCircuitState.HALF_OPEN) {
                if (halfOpenInFlight >= policy.halfOpenMaxCalls()) {
                    recordRejection(GovernanceRejectionReason.HALF_OPEN_LIMITED);
                    return CircuitPermit.rejected(GovernanceRejectionReason.HALF_OPEN_LIMITED);
                }
                halfOpenInFlight++;
                return CircuitPermit.allowed(true);
            }
            return CircuitPermit.allowed(false);
        }

        private synchronized boolean allowRate(GovernancePolicy policy) {
            if (policy.maxRequestsPerWindow() == Integer.MAX_VALUE) {
                return true;
            }
            long now = System.nanoTime();
            long windowNanos = policy.rateLimitWindow().toNanos();
            if (now - windowStartedNanos >= windowNanos) {
                windowStartedNanos = now;
                used = 0;
            }
            if (used >= policy.maxRequestsPerWindow()) {
                return false;
            }
            used++;
            return true;
        }

        private boolean tryAcquire() {
            return semaphore.tryAcquire();
        }

        private void release() {
            semaphore.release();
        }

        private synchronized void recordCall(
                GovernancePolicy policy,
                long startedNanos,
                long endedNanos,
                boolean failed,
                CircuitPermit circuitPermit) {
            boolean slow = policy.slowCallDuration()
                    .map(duration -> endedNanos - startedNanos >= duration.toNanos())
                    .orElse(false);
            if (circuitPermit.halfOpen()) {
                halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
            }
            calls.addLast(new CallRecord(endedNanos, failed, slow));
            pruneCalls(endedNanos);
            consecutiveFailures = failed ? consecutiveFailures + 1 : 0;
            if (!policy.circuitBreakerEnabled()) {
                return;
            }
            if (circuitState == GovernanceCircuitState.HALF_OPEN) {
                if (failed || slow) {
                    openCircuit(policy, endedNanos);
                } else {
                    halfOpenSuccesses++;
                    if (halfOpenSuccesses >= policy.halfOpenMaxCalls() && halfOpenInFlight == 0) {
                        closeCircuit();
                    }
                }
                return;
            }
            if (circuitState != GovernanceCircuitState.CLOSED) {
                return;
            }
            if (failed && consecutiveFailures >= policy.consecutiveFailureThreshold()) {
                openCircuit(policy, endedNanos);
                return;
            }
            WindowCounts counts = windowCounts();
            if (counts.total() < policy.minimumRequests()) {
                return;
            }
            if (percentage(counts.failures(), counts.total()) >= policy.failureRateThreshold()) {
                openCircuit(policy, endedNanos);
                return;
            }
            if (percentage(counts.slowCalls(), counts.total()) >= policy.slowCallThreshold()) {
                openCircuit(policy, endedNanos);
            }
        }

        private synchronized void cancelCircuitPermit(CircuitPermit circuitPermit) {
            if (circuitPermit.halfOpen()) {
                halfOpenInFlight = Math.max(0, halfOpenInFlight - 1);
            }
        }

        private synchronized void recordRejection(GovernanceRejectionReason reason) {
            totalRejections++;
            lastRejectionReason = reason == null ? GovernanceRejectionReason.NONE : reason;
        }

        private synchronized GovernanceRuntimeSnapshot snapshot() {
            transitionOpenToHalfOpenIfReady();
            pruneCalls(System.nanoTime());
            WindowCounts counts = windowCounts();
            return new GovernanceRuntimeSnapshot(
                    resourceKey,
                    priority,
                    circuitState,
                    counts.total(),
                    counts.failures(),
                    counts.slowCalls(),
                    consecutiveFailures,
                    totalRejections,
                    lastRejectionReason,
                    openUntil);
        }

        private void transitionOpenToHalfOpenIfReady() {
            if (circuitState == GovernanceCircuitState.OPEN && openUntil != null && !Instant.now().isBefore(openUntil)) {
                circuitState = GovernanceCircuitState.HALF_OPEN;
                halfOpenInFlight = 0;
                halfOpenSuccesses = 0;
            }
        }

        private void openCircuit(GovernancePolicy policy, long nowNanos) {
            circuitState = GovernanceCircuitState.OPEN;
            openedAtNanos = nowNanos;
            openUntil = Instant.now().plus(policy.openStateDuration());
            halfOpenInFlight = 0;
            halfOpenSuccesses = 0;
        }

        private void closeCircuit() {
            circuitState = GovernanceCircuitState.CLOSED;
            openUntil = null;
            halfOpenInFlight = 0;
            halfOpenSuccesses = 0;
            consecutiveFailures = 0;
            calls.clear();
        }

        private void pruneCalls(long nowNanos) {
            while (!calls.isEmpty() && nowNanos - calls.peekFirst().endedNanos() > lastSlidingWindowNanos) {
                calls.removeFirst();
            }
            while (calls.size() > lastSlidingWindowSize) {
                calls.removeFirst();
            }
        }

        private WindowCounts windowCounts() {
            int failures = 0;
            int slowCalls = 0;
            for (CallRecord call : calls) {
                if (call.failed()) {
                    failures++;
                }
                if (call.slow()) {
                    slowCalls++;
                }
            }
            return new WindowCounts(calls.size(), failures, slowCalls);
        }

        private static double percentage(int part, int total) {
            return total == 0 ? 0.0 : (part * 100.0) / total;
        }
    }

    private static final class CircuitPermit {
        private final boolean allowed;
        private final boolean halfOpen;
        private final GovernanceRejectionReason rejectionReason;

        private CircuitPermit(boolean allowed, boolean halfOpen, GovernanceRejectionReason rejectionReason) {
            this.allowed = allowed;
            this.halfOpen = halfOpen;
            this.rejectionReason = rejectionReason;
        }

        private static CircuitPermit allowed(boolean halfOpen) {
            return new CircuitPermit(true, halfOpen, GovernanceRejectionReason.NONE);
        }

        private static CircuitPermit rejected(GovernanceRejectionReason reason) {
            return new CircuitPermit(false, false, reason);
        }

        private boolean allowed() {
            return allowed;
        }

        private boolean halfOpen() {
            return halfOpen;
        }

        private GovernanceRejectionReason rejectionReason() {
            return rejectionReason;
        }
    }

    private static final class CallRecord {
        private final long endedNanos;
        private final boolean failed;
        private final boolean slow;

        private CallRecord(long endedNanos, boolean failed, boolean slow) {
            this.endedNanos = endedNanos;
            this.failed = failed;
            this.slow = slow;
        }

        private long endedNanos() {
            return endedNanos;
        }

        private boolean failed() {
            return failed;
        }

        private boolean slow() {
            return slow;
        }
    }

    private static final class WindowCounts {
        private final int total;
        private final int failures;
        private final int slowCalls;

        private WindowCounts(int total, int failures, int slowCalls) {
            this.total = total;
            this.failures = failures;
            this.slowCalls = slowCalls;
        }

        private int total() {
            return total;
        }

        private int failures() {
            return failures;
        }

        private int slowCalls() {
            return slowCalls;
        }
    }
}
