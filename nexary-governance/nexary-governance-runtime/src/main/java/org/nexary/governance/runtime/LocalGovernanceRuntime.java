package org.nexary.governance.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.nexary.core.context.CancellationContext;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.fault.FaultSignal;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceIsolationReason;
import org.nexary.core.governance.GovernanceObservationEvents;
import org.nexary.core.governance.GovernancePriority;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.governance.GovernanceTrafficClass;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.core.retry.RetryStopClassifier;
import org.nexary.core.retry.RetryStopReason;

/** Default local governance runtime. */
public final class LocalGovernanceRuntime implements GovernanceRuntime {
    private final GovernancePolicyRegistry policyRegistry;
    private final NexaryObservationPublisher observationPublisher;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final Map<String, GovernanceTrafficClass> firstTrafficClassByResource = new ConcurrentHashMap<>();
    private final Map<String, Boolean> mixedTrafficReported = new ConcurrentHashMap<>();
    private final ArrayDeque<GovernanceRuntimeEvent> recentEvents = new ArrayDeque<>();
    private final int recentEventCapacity;

    /** Creates a runtime with the default bounded diagnostics event capacity. */
    public LocalGovernanceRuntime(GovernancePolicyRegistry policyRegistry, NexaryObservationPublisher observationPublisher) {
        this(policyRegistry, observationPublisher, 256);
    }

    /** Creates a runtime with an explicit bounded diagnostics event capacity. */
    public LocalGovernanceRuntime(
            GovernancePolicyRegistry policyRegistry,
            NexaryObservationPublisher observationPublisher,
            int recentEventCapacity) {
        this.policyRegistry = policyRegistry == null
                ? LocalGovernancePolicyRegistry.builder().build()
                : policyRegistry;
        this.observationPublisher = observationPublisher == null
                ? NexaryObservationPublisher.noop()
                : observationPublisher;
        this.recentEventCapacity = Math.max(1, recentEventCapacity);
    }

    @Override
    public List<GovernanceResourceDescriptor> resources() {
        Map<String, GovernanceResourceDescriptor> resources = new ConcurrentHashMap<>();
        for (GovernanceResourceDescriptor descriptor : policyRegistry.resources()) {
            resources.put(
                    descriptor.resourceKey() + ":" + descriptor.trafficClass() + ":" + descriptor.priority(),
                    descriptor);
        }
        for (State state : states.values()) {
            GovernanceResourceDescriptor descriptor = state.descriptor();
            resources.put(
                    descriptor.resourceKey() + ":" + descriptor.trafficClass() + ":" + descriptor.priority(),
                    descriptor);
        }
        List<GovernanceResourceDescriptor> sorted = new ArrayList<>(resources.values());
        sorted.sort(Comparator
                .comparing(GovernanceResourceDescriptor::resourceKey)
                .thenComparing(GovernanceResourceDescriptor::trafficClass)
                .thenComparing(GovernanceResourceDescriptor::priority));
        return sorted;
    }

    /** Returns low-cardinality diagnostic snapshots for current local resource states. */
    @Override
    public List<GovernanceRuntimeSnapshot> snapshots() {
        List<GovernanceRuntimeSnapshot> snapshots = new ArrayList<>();
        for (State state : states.values()) {
            snapshots.add(state.snapshot());
        }
        snapshots.sort(Comparator
                .comparing(GovernanceRuntimeSnapshot::resourceKey)
                .thenComparing(GovernanceRuntimeSnapshot::trafficClass)
                .thenComparing(GovernanceRuntimeSnapshot::priority));
        return snapshots;
    }

    @Override
    public List<GovernanceRuntimeEvent> recentEvents() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    @Override
    public GovernanceRuntimeSummary summary() {
        List<GovernanceRuntimeSnapshot> currentSnapshots = snapshots();
        List<GovernanceRuntimeEvent> currentEvents = recentEvents();
        long successes = 0L;
        long failures = 0L;
        long rejected = 0L;
        long fallback = 0L;
        long cancelled = 0L;
        long retryStopped = 0L;
        long isolated = 0L;
        Map<String, Long> trafficClassCounts = new LinkedHashMap<>();
        Map<String, Long> priorityCounts = new LinkedHashMap<>();
        Instant lastEventAt = null;
        for (GovernanceRuntimeEvent event : currentEvents) {
            trafficClassCounts.merge(event.trafficClass().diagnosticName(), 1L, Long::sum);
            priorityCounts.merge(event.priority().diagnosticName(), 1L, Long::sum);
            if (event.outcome() == GovernanceCallOutcome.SUCCESS) {
                successes++;
            } else if (event.outcome() == GovernanceCallOutcome.FAILURE) {
                failures++;
            } else if (event.outcome() == GovernanceCallOutcome.REJECTED
                    || event.outcome() == GovernanceCallOutcome.ISOLATED) {
                rejected++;
            } else if (event.outcome() == GovernanceCallOutcome.CANCELLED) {
                cancelled++;
            }
            if (actualIsolation(event.isolationReason())) {
                isolated++;
            }
            if (event.action() == GovernanceRuntimeAction.FALLBACK) {
                fallback++;
            }
            if (event.retryStopReason() != RetryStopReason.NONE) {
                retryStopped++;
            }
            if (lastEventAt == null || event.timestamp().isAfter(lastEventAt)) {
                lastEventAt = event.timestamp();
            }
        }
        long openCircuits = currentSnapshots.stream()
                .filter(snapshot -> snapshot.circuitState() == GovernanceCircuitState.OPEN)
                .count();
        long halfOpenCircuits = currentSnapshots.stream()
                .filter(snapshot -> snapshot.circuitState() == GovernanceCircuitState.HALF_OPEN)
                .count();
        long degradedResources = currentSnapshots.stream()
                .filter(GovernanceRuntimeSnapshot::degraded)
                .count();
        return new GovernanceRuntimeSummary(
                resources().size(),
                currentSnapshots.size(),
                currentEvents.size(),
                successes,
                failures,
                rejected,
                fallback,
                cancelled,
                retryStopped,
                0L,
                isolated,
                0L,
                trafficClassCounts,
                priorityCounts,
                openCircuits,
                halfOpenCircuits,
                degradedResources,
                lastEventAt);
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
        recordMixedTrafficWarning(state, startedAt);
        GovernanceDecision cancelled = cancelBeforeAcquire(effectiveContext, state);
        if (cancelled != null) {
            recordRuntimeEvent(
                    state,
                    GovernanceRuntimeAction.CANCEL,
                    GovernanceCallOutcome.CANCELLED,
                    GovernanceRejectionReason.NONE,
                    state.lastCancellationReason,
                    RetryStopClassifier.fromCancellationReason(state.lastCancellationReason),
                    startedAt,
                    Instant.now());
            return fallbackOrThrow(fallback, cancelled);
        }
        GovernanceDecision rejected = rejectBeforeAcquire(effectiveContext, state, policy);
        if (rejected != null) {
            publishRejection(effectiveContext, rejected, startedAt);
            recordRuntimeEvent(
                    state,
                    fallback == null ? GovernanceRuntimeAction.REJECT : GovernanceRuntimeAction.FALLBACK,
                    outcomeForIsolation(isolationReason(state, state.lastRejectionReason)),
                    state.lastRejectionReason,
                    isolationReason(state, state.lastRejectionReason),
                    CancellationReason.NONE,
                    retryStopReason(rejected),
                    startedAt,
                    Instant.now());
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
            recordRuntimeEvent(
                    state,
                    fallback == null ? GovernanceRuntimeAction.REJECT : GovernanceRuntimeAction.FALLBACK,
                    outcomeForIsolation(isolationReason(state, circuitPermit.rejectionReason())),
                    circuitPermit.rejectionReason(),
                    isolationReason(state, circuitPermit.rejectionReason()),
                    CancellationReason.NONE,
                    retryStopReason(decision),
                    startedAt,
                    Instant.now());
            return fallbackOrThrow(fallback, decision);
        }
        if (!state.allowRate(policy)) {
            state.cancelCircuitPermit(circuitPermit);
            state.recordRejection(GovernanceRejectionReason.RATE_LIMITED);
            GovernanceDecision decision = GovernanceDecision.rejected(
                    GovernanceDecision.Decision.RATE_LIMITED,
                    new FaultSignal(FaultSignal.FaultType.RATE_LIMITED, "governance", "rate limited", Instant.now(), true));
            publishRejection(effectiveContext, decision, startedAt);
            recordRuntimeEvent(
                    state,
                    fallback == null ? GovernanceRuntimeAction.REJECT : GovernanceRuntimeAction.FALLBACK,
                    outcomeForIsolation(isolationReason(state, GovernanceRejectionReason.RATE_LIMITED)),
                    GovernanceRejectionReason.RATE_LIMITED,
                    isolationReason(state, GovernanceRejectionReason.RATE_LIMITED),
                    CancellationReason.NONE,
                    retryStopReason(decision),
                    startedAt,
                    Instant.now());
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
            recordRuntimeEvent(
                    state,
                    fallback == null ? GovernanceRuntimeAction.REJECT : GovernanceRuntimeAction.FALLBACK,
                    outcomeForIsolation(isolationReason(state, GovernanceRejectionReason.CONCURRENCY_LIMITED)),
                    GovernanceRejectionReason.CONCURRENCY_LIMITED,
                    isolationReason(state, GovernanceRejectionReason.CONCURRENCY_LIMITED),
                    CancellationReason.NONE,
                    retryStopReason(decision),
                    startedAt,
                    Instant.now());
            return fallbackOrThrow(fallback, decision);
        }
        long actionStartedNanos = System.nanoTime();
        Throwable failure = null;
        CancellationReason completionCancellationReason = CancellationReason.NONE;
        try {
            T result = GovernanceContext.callWithContext(effectiveContext, action);
            completionCancellationReason = cancellationReason(effectiveContext);
            if (completionCancellationReason != CancellationReason.NONE) {
                state.recordCancellation(completionCancellationReason);
            }
            return result;
        } catch (Throwable error) {
            failure = error;
            return rethrow(error);
        } finally {
            state.recordCall(policy, actionStartedNanos, System.nanoTime(), failure != null, circuitPermit);
            recordRuntimeEvent(
                    state,
                    completionCancellationReason == CancellationReason.NONE
                            ? GovernanceRuntimeAction.EXECUTE
                            : GovernanceRuntimeAction.CANCEL,
                    failure == null
                            ? completionCancellationReason == CancellationReason.NONE
                                    ? GovernanceCallOutcome.SUCCESS
                                    : GovernanceCallOutcome.CANCELLED
                            : GovernanceCallOutcome.FAILURE,
                    GovernanceRejectionReason.NONE,
                    completionCancellationReason,
                    RetryStopClassifier.fromCancellationReason(completionCancellationReason),
                    startedAt,
                    Instant.now());
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

    private GovernanceDecision cancelBeforeAcquire(GovernanceContext context, State state) {
        CancellationReason reason = cancellationReason(context);
        if (reason == CancellationReason.NONE) {
            return null;
        }
        state.recordCancellation(reason);
        FaultSignal.FaultType faultType = reason == CancellationReason.DEADLINE_EXPIRED
                ? FaultSignal.FaultType.TIMEOUT
                : FaultSignal.FaultType.REJECTED;
        return GovernanceDecision.rejected(
                GovernanceDecision.Decision.CANCELLED,
                new FaultSignal(faultType, "governance", "cancelled", Instant.now(), false));
    }

    private CancellationReason cancellationReason(GovernanceContext context) {
        if (context.cancellationToken().isPresent() && context.cancellationToken().get().isCancelled()) {
            return context.cancellationToken().get().reason();
        }
        return CancellationContext.reason();
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
        GovernanceTrafficClass trafficClass = context.trafficClass();
        GovernancePriority priority = context.governancePriority();
        String key = context.resource().key() + ":"
                + trafficClass.diagnosticName() + ":"
                + priority.diagnosticName();
        return states.compute(key, (ignored, current) -> {
            if (current != null && current.maxConcurrency == policy.maxConcurrency()) {
                return current;
            }
            return new State(context.resource(), trafficClass, priority, policy.maxConcurrency());
        });
    }

    private void recordMixedTrafficWarning(State state, Instant startedAt) {
        GovernanceTrafficClass previous = firstTrafficClassByResource.putIfAbsent(state.resourceKey, state.trafficClass);
        if (previous == null || previous == state.trafficClass) {
            return;
        }
        if (mixedTrafficReported.putIfAbsent(state.resourceKey, Boolean.TRUE) != null) {
            return;
        }
        recordRuntimeEvent(
                state,
                GovernanceRuntimeAction.WARN,
                GovernanceCallOutcome.NONE,
                GovernanceRejectionReason.NONE,
                GovernanceIsolationReason.MIXED_TRAFFIC,
                CancellationReason.NONE,
                RetryStopReason.NONE,
                startedAt,
                Instant.now());
    }

    private void recordRuntimeEvent(
            State state,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            CancellationReason cancellationReason,
            RetryStopReason retryStopReason,
            Instant startedAt,
            Instant endedAt) {
        recordRuntimeEvent(
                state,
                action,
                outcome,
                rejectionReason,
                GovernanceIsolationReason.NONE,
                cancellationReason,
                retryStopReason,
                startedAt,
                endedAt);
    }

    private void recordRuntimeEvent(
            State state,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            GovernanceIsolationReason isolationReason,
            CancellationReason cancellationReason,
            RetryStopReason retryStopReason,
            Instant startedAt,
            Instant endedAt) {
        Duration duration = startedAt == null || endedAt == null
                ? null
                : Duration.between(startedAt, endedAt);
        GovernanceRuntimeEvent event = new GovernanceRuntimeEvent(
                state.resourceKey,
                state.trafficClass,
                state.priority,
                action,
                outcome,
                rejectionReason,
                isolationReason,
                cancellationReason,
                GovernanceEngine.LOCAL,
                GovernanceBlockReason.NONE,
                retryStopReason,
                state.currentCircuitState(),
                endedAt,
                GovernanceDurationBucket.from(duration));
        state.recordIsolation(isolationReason);
        state.recordRetryStop(retryStopReason);
        synchronized (recentEvents) {
            recentEvents.addLast(event);
            while (recentEvents.size() > recentEventCapacity) {
                recentEvents.removeFirst();
            }
        }
    }

    private static RetryStopReason retryStopReason(GovernanceDecision decision) {
        return decision == null || decision.retrySignal() == null
                ? RetryStopReason.NONE
                : decision.retrySignal().stopReason();
    }

    private static GovernanceIsolationReason isolationReason(State state, GovernanceRejectionReason rejectionReason) {
        if (state == null || state.priority != GovernancePriority.LOW) {
            return GovernanceIsolationReason.NONE;
        }
        if (rejectionReason == GovernanceRejectionReason.RATE_LIMITED) {
            return GovernanceIsolationReason.PRIORITY_RATE_LIMITED;
        }
        if (rejectionReason == GovernanceRejectionReason.CONCURRENCY_LIMITED) {
            return GovernanceIsolationReason.PRIORITY_BULKHEAD_FULL;
        }
        if (rejectionReason == GovernanceRejectionReason.DEGRADED) {
            return GovernanceIsolationReason.PRIORITY_DEGRADED;
        }
        if (rejectionReason == GovernanceRejectionReason.CIRCUIT_OPEN
                || rejectionReason == GovernanceRejectionReason.HALF_OPEN_LIMITED) {
            return GovernanceIsolationReason.PRIORITY_CIRCUIT_OPEN;
        }
        return GovernanceIsolationReason.NONE;
    }

    private static GovernanceCallOutcome outcomeForIsolation(GovernanceIsolationReason reason) {
        return actualIsolation(reason) ? GovernanceCallOutcome.ISOLATED : GovernanceCallOutcome.REJECTED;
    }

    private static boolean actualIsolation(GovernanceIsolationReason reason) {
        return reason != null
                && reason != GovernanceIsolationReason.NONE
                && reason != GovernanceIsolationReason.MIXED_TRAFFIC;
    }

    private static final class State {
        private final String resourceKey;
        private final GovernanceResource.ResourceKind kind;
        private final String name;
        private final String provider;
        private final String operation;
        private final GovernanceTrafficClass trafficClass;
        private final GovernancePriority priority;
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
        private GovernanceIsolationReason lastIsolationReason = GovernanceIsolationReason.NONE;
        private CancellationReason lastCancellationReason = CancellationReason.NONE;
        private RetryStopReason lastRetryStopReason = RetryStopReason.NONE;
        private Instant lastStateTransitionAt = Instant.now();
        private GovernanceCallOutcome lastOutcome = GovernanceCallOutcome.NONE;
        private Instant lastOutcomeAt;
        private int lastMaxRequestsPerWindow = Integer.MAX_VALUE;
        private Duration lastRateLimitWindow = Duration.ofSeconds(1);
        private boolean lastDegraded;
        private int lastMinimumRequests = 100;
        private double lastFailureRateThreshold = Double.POSITIVE_INFINITY;
        private double lastSlowCallThreshold = Double.POSITIVE_INFINITY;
        private Duration lastSlowCallDuration;
        private Duration lastOpenStateDuration = Duration.ofSeconds(60);
        private int lastHalfOpenMaxCalls = 1;
        private int lastSlidingWindowSize = 100;
        private Duration lastSlidingWindowDuration = Duration.ofSeconds(60);
        private long lastSlidingWindowNanos = Duration.ofSeconds(60).toNanos();
        private int lastConsecutiveFailureThreshold = Integer.MAX_VALUE;
        private GovernancePolicySnapshot lastPolicySnapshot = GovernancePolicySnapshot.from(GovernancePolicy.allowAll());

        private State(
                GovernanceResource resource,
                GovernanceTrafficClass trafficClass,
                GovernancePriority priority,
                int maxConcurrency) {
            GovernanceResource safeResource = resource == null ? GovernanceResource.custom("unknown", "default") : resource;
            this.resourceKey = safeResource.key();
            this.kind = safeResource.kind();
            this.name = safeResource.name();
            this.provider = safeResource.provider();
            this.operation = safeResource.operation();
            this.trafficClass = trafficClass == null ? GovernanceTrafficClass.ONLINE : trafficClass;
            this.priority = priority == null ? GovernancePriority.NORMAL : priority;
            this.maxConcurrency = maxConcurrency;
            this.semaphore = new Semaphore(maxConcurrency);
        }

        private synchronized void touchPolicy(GovernancePolicy policy) {
            lastMaxRequestsPerWindow = policy.maxRequestsPerWindow();
            lastRateLimitWindow = policy.rateLimitWindow();
            lastDegraded = policy.degraded();
            lastMinimumRequests = policy.minimumRequests();
            lastFailureRateThreshold = policy.failureRateThreshold();
            lastSlowCallThreshold = policy.slowCallThreshold();
            lastSlowCallDuration = policy.slowCallDuration().orElse(null);
            lastOpenStateDuration = policy.openStateDuration();
            lastHalfOpenMaxCalls = policy.halfOpenMaxCalls();
            lastSlidingWindowSize = policy.slidingWindowSize();
            lastSlidingWindowDuration = policy.slidingWindowDuration();
            lastSlidingWindowNanos = lastSlidingWindowDuration.toNanos();
            lastConsecutiveFailureThreshold = policy.consecutiveFailureThreshold();
            lastPolicySnapshot = GovernancePolicySnapshot.from(policy);
            pruneCalls(System.nanoTime());
        }

        private synchronized CircuitPermit tryEnterCircuit(GovernancePolicy policy) {
            if (!policy.circuitBreakerEnabled()) {
                transitionTo(GovernanceCircuitState.CLOSED, Instant.now());
                halfOpenInFlight = 0;
                halfOpenSuccesses = 0;
                openUntil = null;
                return CircuitPermit.allowed(false);
            }
            long now = System.nanoTime();
            pruneCalls(now);
            if (circuitState == GovernanceCircuitState.OPEN
                    && now - openedAtNanos >= policy.openStateDuration().toNanos()) {
                transitionTo(GovernanceCircuitState.HALF_OPEN, Instant.now());
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
            lastOutcome = failed ? GovernanceCallOutcome.FAILURE : GovernanceCallOutcome.SUCCESS;
            lastOutcomeAt = Instant.now();
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
            lastOutcome = GovernanceCallOutcome.REJECTED;
            lastOutcomeAt = Instant.now();
        }

        private synchronized void recordCancellation(CancellationReason reason) {
            lastCancellationReason = reason == null ? CancellationReason.NONE : reason;
            lastOutcome = GovernanceCallOutcome.CANCELLED;
            lastOutcomeAt = Instant.now();
        }

        private synchronized void recordRetryStop(RetryStopReason reason) {
            if (reason != null && reason != RetryStopReason.NONE) {
                lastRetryStopReason = reason;
            }
        }

        private synchronized void recordIsolation(GovernanceIsolationReason reason) {
            if (reason != null && reason != GovernanceIsolationReason.NONE) {
                lastIsolationReason = reason;
            }
        }

        private synchronized GovernanceResourceDescriptor descriptor() {
            return new GovernanceResourceDescriptor(
                    resourceKey,
                    kind,
                    name,
                    provider,
                    operation,
                    trafficClass.diagnosticName(),
                    priority.diagnosticName(),
                    GovernanceEngine.LOCAL,
                    lastPolicySnapshot,
                    snapshot());
        }

        private synchronized GovernanceRuntimeSnapshot snapshot() {
            transitionOpenToHalfOpenIfReady();
            pruneCalls(System.nanoTime());
            WindowCounts counts = windowCounts();
            return new GovernanceRuntimeSnapshot(
                    resourceKey,
                    trafficClass.diagnosticName(),
                    priority.diagnosticName(),
                    GovernanceEngine.LOCAL,
                    circuitState,
                    counts.total(),
                    counts.failures(),
                    counts.slowCalls(),
                    consecutiveFailures,
                    totalRejections,
                    lastRejectionReason,
                    GovernanceBlockReason.NONE,
                    lastIsolationReason,
                    lastCancellationReason,
                    lastRetryStopReason,
                    openUntil,
                    activeConcurrency(),
                    maxConcurrency,
                    lastMaxRequestsPerWindow,
                    lastRateLimitWindow,
                    lastDegraded,
                    lastMinimumRequests,
                    lastFailureRateThreshold,
                    lastSlowCallThreshold,
                    lastSlowCallDuration,
                    lastOpenStateDuration,
                    lastHalfOpenMaxCalls,
                    lastSlidingWindowSize,
                    lastSlidingWindowDuration,
                    lastConsecutiveFailureThreshold,
                    lastStateTransitionAt,
                    lastOutcome,
                    lastOutcomeAt);
        }

        private synchronized GovernanceCircuitState currentCircuitState() {
            transitionOpenToHalfOpenIfReady();
            return circuitState;
        }

        private void transitionOpenToHalfOpenIfReady() {
            Instant now = Instant.now();
            if (circuitState == GovernanceCircuitState.OPEN && openUntil != null && !now.isBefore(openUntil)) {
                transitionTo(GovernanceCircuitState.HALF_OPEN, now);
                halfOpenInFlight = 0;
                halfOpenSuccesses = 0;
            }
        }

        private void openCircuit(GovernancePolicy policy, long nowNanos) {
            Instant now = Instant.now();
            transitionTo(GovernanceCircuitState.OPEN, now);
            openedAtNanos = nowNanos;
            openUntil = now.plus(policy.openStateDuration());
            halfOpenInFlight = 0;
            halfOpenSuccesses = 0;
        }

        private void closeCircuit() {
            transitionTo(GovernanceCircuitState.CLOSED, Instant.now());
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

        private int activeConcurrency() {
            return Math.max(0, maxConcurrency - semaphore.availablePermits());
        }

        private void transitionTo(GovernanceCircuitState nextState, Instant transitionAt) {
            if (circuitState != nextState) {
                circuitState = nextState;
                lastStateTransitionAt = transitionAt;
            }
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
