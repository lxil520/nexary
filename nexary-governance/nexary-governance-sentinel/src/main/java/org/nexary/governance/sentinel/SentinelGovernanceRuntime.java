package org.nexary.governance.sentinel;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.nexary.core.context.CancellationContext;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.fault.FaultSignal;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceObservationEvents;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.governance.runtime.GovernanceBlockReason;
import org.nexary.governance.runtime.GovernanceCallOutcome;
import org.nexary.governance.runtime.GovernanceCircuitState;
import org.nexary.governance.runtime.GovernanceDecision;
import org.nexary.governance.runtime.GovernanceDurationBucket;
import org.nexary.governance.runtime.GovernanceEngine;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernancePolicyRegistry;
import org.nexary.governance.runtime.GovernancePolicySnapshot;
import org.nexary.governance.runtime.GovernanceRejectedException;
import org.nexary.governance.runtime.GovernanceRejectionReason;
import org.nexary.governance.runtime.GovernanceResourceDescriptor;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.GovernanceRuntimeAction;
import org.nexary.governance.runtime.GovernanceRuntimeEvent;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.GovernanceRuntimeSummary;
import org.nexary.governance.runtime.LocalGovernancePolicyRegistry;

/** Sentinel-backed governance runtime for Nexary resources. */
public final class SentinelGovernanceRuntime implements GovernanceRuntime {
    private final GovernancePolicyRegistry policyRegistry;
    private final NexaryObservationPublisher observationPublisher;
    private final SentinelRuleMapper ruleMapper;
    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final ArrayDeque<GovernanceRuntimeEvent> recentEvents = new ArrayDeque<>();
    private final int recentEventCapacity;
    private final Object ruleLock = new Object();
    private final Map<String, List<FlowRule>> flowRulesByState = new LinkedHashMap<>();
    private final Map<String, List<DegradeRule>> degradeRulesByState = new LinkedHashMap<>();

    /** Creates a Sentinel runtime with default diagnostics event capacity. */
    public SentinelGovernanceRuntime(
            GovernancePolicyRegistry policyRegistry,
            NexaryObservationPublisher observationPublisher) {
        this(policyRegistry, observationPublisher, new SentinelRuleMapper(), 256);
    }

    /** Creates a Sentinel runtime with explicit mapper and diagnostics event capacity. */
    public SentinelGovernanceRuntime(
            GovernancePolicyRegistry policyRegistry,
            NexaryObservationPublisher observationPublisher,
            SentinelRuleMapper ruleMapper,
            int recentEventCapacity) {
        this.policyRegistry = policyRegistry == null
                ? LocalGovernancePolicyRegistry.builder().build()
                : policyRegistry;
        this.observationPublisher = observationPublisher == null
                ? NexaryObservationPublisher.noop()
                : observationPublisher;
        this.ruleMapper = ruleMapper == null ? new SentinelRuleMapper() : ruleMapper;
        this.recentEventCapacity = Math.max(1, recentEventCapacity);
        loadConfiguredRules();
    }

    @Override
    public List<GovernanceResourceDescriptor> resources() {
        Map<String, GovernanceResourceDescriptor> descriptors = new LinkedHashMap<>();
        for (GovernanceResourceDescriptor descriptor : policyRegistry.resources()) {
            descriptors.put(descriptor.resourceKey() + ":" + descriptor.priority(), sentinelDescriptor(descriptor));
        }
        for (State state : states.values()) {
            GovernanceResourceDescriptor descriptor = state.descriptor();
            descriptors.put(descriptor.resourceKey() + ":" + descriptor.priority(), descriptor);
        }
        List<GovernanceResourceDescriptor> sorted = new ArrayList<>(descriptors.values());
        sorted.sort(Comparator
                .comparing(GovernanceResourceDescriptor::resourceKey)
                .thenComparing(GovernanceResourceDescriptor::priority));
        return sorted;
    }

    @Override
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
        long blocked = 0L;
        Instant lastEventAt = null;
        for (GovernanceRuntimeEvent event : currentEvents) {
            if (event.outcome() == GovernanceCallOutcome.SUCCESS) {
                successes++;
            } else if (event.outcome() == GovernanceCallOutcome.FAILURE) {
                failures++;
            } else if (event.outcome() == GovernanceCallOutcome.REJECTED) {
                rejected++;
            } else if (event.outcome() == GovernanceCallOutcome.CANCELLED) {
                cancelled++;
            }
            if (event.action() == GovernanceRuntimeAction.FALLBACK) {
                fallback++;
            }
            if (event.blockReason() != GovernanceBlockReason.NONE) {
                blocked++;
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
        long sentinelResources = resources().stream()
                .filter(descriptor -> descriptor.engine() == GovernanceEngine.SENTINEL)
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
                blocked,
                sentinelResources,
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
        ensureRules(state.stateKey, state.resourceKey, policy);

        GovernanceDecision cancelled = cancelBeforeAcquire(effectiveContext, state);
        if (cancelled != null) {
            recordRuntimeEvent(
                    state,
                    GovernanceRuntimeAction.CANCEL,
                    GovernanceCallOutcome.CANCELLED,
                    GovernanceRejectionReason.NONE,
                    GovernanceBlockReason.NONE,
                    state.lastCancellationReason,
                    startedAt,
                    Instant.now());
            return fallbackOrThrow(fallback, cancelled);
        }
        GovernanceDecision rejected = rejectBeforeSentinel(effectiveContext, state, policy);
        if (rejected != null) {
            publishRejection(effectiveContext, rejected, startedAt);
            recordRuntimeEvent(
                    state,
                    fallback == null ? GovernanceRuntimeAction.REJECT : GovernanceRuntimeAction.FALLBACK,
                    GovernanceCallOutcome.REJECTED,
                    state.lastRejectionReason,
                    state.lastBlockReason,
                    CancellationReason.NONE,
                    startedAt,
                    Instant.now());
            return fallbackOrThrow(fallback, rejected);
        }

        Entry entry = null;
        long actionStartedNanos = System.nanoTime();
        Throwable failure = null;
        CancellationReason completionCancellationReason = CancellationReason.NONE;
        try {
            entry = SphU.entry(effectiveContext.resource().key());
            state.activeConcurrency.incrementAndGet();
            T result = GovernanceContext.callWithContext(effectiveContext, action);
            completionCancellationReason = cancellationReason(effectiveContext);
            if (completionCancellationReason != CancellationReason.NONE) {
                state.recordCancellation(completionCancellationReason);
            }
            return result;
        } catch (BlockException blocked) {
            GovernanceBlockReason blockReason = blockReason(blocked);
            GovernanceRejectionReason rejectionReason = rejectionReason(blockReason);
            state.recordBlock(blockReason, rejectionReason);
            GovernanceDecision decision = decisionFor(blockReason);
            publishBlock(effectiveContext, decision, rejectionReason, startedAt);
            recordRuntimeEvent(
                    state,
                    fallback == null ? GovernanceRuntimeAction.REJECT : GovernanceRuntimeAction.FALLBACK,
                    GovernanceCallOutcome.REJECTED,
                    rejectionReason,
                    blockReason,
                    CancellationReason.NONE,
                    startedAt,
                    Instant.now());
            return fallbackOrThrow(fallback, decision);
        } catch (Throwable error) {
            failure = error;
            if (entry != null) {
                Tracer.traceEntry(error, entry);
            }
            return rethrow(error);
        } finally {
            if (entry != null) {
                entry.exit();
                state.activeConcurrency.updateAndGet(current -> Math.max(0, current - 1));
            }
            state.recordCall(policy, actionStartedNanos, System.nanoTime(), failure != null, completionCancellationReason);
            if (entry != null) {
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
                        GovernanceBlockReason.NONE,
                        completionCancellationReason,
                        startedAt,
                        Instant.now());
            }
        }
    }

    private void loadConfiguredRules() {
        synchronized (ruleLock) {
            for (GovernanceResourceDescriptor descriptor : policyRegistry.resources()) {
                String stateKey = descriptor.resourceKey() + ":" + descriptor.priority();
                GovernancePolicy policy = policyFromSnapshot(descriptor.policySnapshot());
                flowRulesByState.put(stateKey, ruleMapper.flowRules(descriptor.resourceKey(), policy));
                degradeRulesByState.put(stateKey, ruleMapper.degradeRules(descriptor.resourceKey(), policy));
            }
            publishRules();
        }
    }

    private void ensureRules(String stateKey, String resourceKey, GovernancePolicy policy) {
        synchronized (ruleLock) {
            flowRulesByState.put(stateKey, ruleMapper.flowRules(resourceKey, policy));
            degradeRulesByState.put(stateKey, ruleMapper.degradeRules(resourceKey, policy));
            publishRules();
        }
    }

    private void publishRules() {
        List<FlowRule> flowRules = new ArrayList<>();
        for (List<FlowRule> rules : flowRulesByState.values()) {
            flowRules.addAll(rules);
        }
        FlowRuleManager.loadRules(flowRules);
        List<DegradeRule> degradeRules = new ArrayList<>();
        for (List<DegradeRule> rules : degradeRulesByState.values()) {
            degradeRules.addAll(rules);
        }
        DegradeRuleManager.loadRules(degradeRules);
    }

    private State stateFor(GovernanceContext context, GovernancePolicy policy) {
        String priority = context.priority().name().toLowerCase();
        String stateKey = context.resource().key() + ":" + priority;
        return states.compute(stateKey, (ignored, current) -> {
            if (current != null) {
                return current;
            }
            return new State(stateKey, context.resource(), priority, policy);
        });
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
        context.cancellationToken().ifPresent(builder::cancellationToken);
        return builder.build();
    }

    private GovernanceDecision rejectBeforeSentinel(GovernanceContext context, State state, GovernancePolicy policy) {
        if (context.deadlineExpired()) {
            state.recordRejection(GovernanceRejectionReason.DEADLINE_EXPIRED, GovernanceBlockReason.NONE);
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.DEADLINE_EXPIRED,
                    new FaultSignal(FaultSignal.FaultType.TIMEOUT, "governance", "deadline expired", Instant.now(), false));
        }
        if (policy.degraded()) {
            state.recordRejection(GovernanceRejectionReason.DEGRADED, GovernanceBlockReason.DEGRADED);
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

    private void publishBlock(
            GovernanceContext context,
            GovernanceDecision decision,
            GovernanceRejectionReason rejectionReason,
            Instant startedAt) {
        Instant endedAt = Instant.now();
        if (rejectionReason == GovernanceRejectionReason.RATE_LIMITED) {
            observationPublisher.publish(GovernanceObservationEvents.rateLimited(
                    context.resource(), context.trafficTag(), startedAt, endedAt));
        } else if (rejectionReason == GovernanceRejectionReason.CONCURRENCY_LIMITED) {
            observationPublisher.publish(GovernanceObservationEvents.bulkheadRejected(
                    context.resource(), context.trafficTag(), startedAt, endedAt));
        } else if (rejectionReason == GovernanceRejectionReason.CIRCUIT_OPEN) {
            observationPublisher.publish(GovernanceObservationEvents.circuitOpen(
                    context.resource(), context.trafficTag(), "sentinel", startedAt, endedAt));
        }
        publishRetryStopped(context, decision, startedAt);
    }

    private void publishRejection(GovernanceContext context, GovernanceDecision decision, Instant startedAt) {
        Instant endedAt = Instant.now();
        if (decision.decision() == GovernanceDecision.Decision.DEADLINE_EXPIRED) {
            observationPublisher.publish(GovernanceObservationEvents.deadlineExceeded(
                    context.resource(), context.trafficTag(), startedAt, endedAt));
        } else if (decision.decision() == GovernanceDecision.Decision.DEGRADED) {
            observationPublisher.publish(GovernanceObservationEvents.degraded(
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

    private GovernanceDecision decisionFor(GovernanceBlockReason reason) {
        if (reason == GovernanceBlockReason.RATE_LIMITED) {
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.RATE_LIMITED,
                    new FaultSignal(FaultSignal.FaultType.RATE_LIMITED, "governance", "rate limited", Instant.now(), true));
        }
        if (reason == GovernanceBlockReason.BULKHEAD_FULL) {
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.CONCURRENCY_LIMITED,
                    new FaultSignal(FaultSignal.FaultType.REJECTED, "governance", "concurrency limited", Instant.now(), true));
        }
        if (reason == GovernanceBlockReason.CIRCUIT_OPEN) {
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.CIRCUIT_OPEN,
                    new FaultSignal(FaultSignal.FaultType.REJECTED, "governance", "circuit open", Instant.now(), true));
        }
        if (reason == GovernanceBlockReason.DEGRADED) {
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.DEGRADED,
                    new FaultSignal(FaultSignal.FaultType.DEGRADED, "governance", "degraded", Instant.now(), false));
        }
        return GovernanceDecision.rejected(
                GovernanceDecision.Decision.REJECTED,
                new FaultSignal(FaultSignal.FaultType.REJECTED, "governance", "blocked", Instant.now(), true));
    }

    private GovernanceBlockReason blockReason(BlockException blocked) {
        if (blocked instanceof FlowException) {
            FlowRule rule = ((FlowException) blocked).getRule();
            if (rule != null && rule.getGrade() == RuleConstant.FLOW_GRADE_THREAD) {
                return GovernanceBlockReason.BULKHEAD_FULL;
            }
            return GovernanceBlockReason.RATE_LIMITED;
        }
        if (blocked instanceof DegradeException) {
            return GovernanceBlockReason.CIRCUIT_OPEN;
        }
        return GovernanceBlockReason.UNKNOWN;
    }

    private GovernanceRejectionReason rejectionReason(GovernanceBlockReason reason) {
        if (reason == GovernanceBlockReason.RATE_LIMITED) {
            return GovernanceRejectionReason.RATE_LIMITED;
        }
        if (reason == GovernanceBlockReason.BULKHEAD_FULL) {
            return GovernanceRejectionReason.CONCURRENCY_LIMITED;
        }
        if (reason == GovernanceBlockReason.CIRCUIT_OPEN) {
            return GovernanceRejectionReason.CIRCUIT_OPEN;
        }
        if (reason == GovernanceBlockReason.DEGRADED) {
            return GovernanceRejectionReason.DEGRADED;
        }
        return GovernanceRejectionReason.NONE;
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

    private void recordRuntimeEvent(
            State state,
            GovernanceRuntimeAction action,
            GovernanceCallOutcome outcome,
            GovernanceRejectionReason rejectionReason,
            GovernanceBlockReason blockReason,
            CancellationReason cancellationReason,
            Instant startedAt,
            Instant endedAt) {
        Duration duration = startedAt == null || endedAt == null
                ? null
                : Duration.between(startedAt, endedAt);
        GovernanceRuntimeEvent event = new GovernanceRuntimeEvent(
                state.resourceKey,
                action,
                outcome,
                rejectionReason,
                cancellationReason,
                GovernanceEngine.SENTINEL,
                blockReason,
                state.currentCircuitState(),
                endedAt,
                GovernanceDurationBucket.from(duration));
        synchronized (recentEvents) {
            recentEvents.addLast(event);
            while (recentEvents.size() > recentEventCapacity) {
                recentEvents.removeFirst();
            }
        }
    }

    private static GovernanceResourceDescriptor sentinelDescriptor(GovernanceResourceDescriptor descriptor) {
        return new GovernanceResourceDescriptor(
                descriptor.resourceKey(),
                descriptor.kind(),
                descriptor.name(),
                descriptor.provider(),
                descriptor.operation(),
                descriptor.priority(),
                GovernanceEngine.SENTINEL,
                descriptor.policySnapshot(),
                descriptor.runtimeSnapshot());
    }

    private static GovernancePolicy policyFromSnapshot(GovernancePolicySnapshot snapshot) {
        GovernancePolicySnapshot safeSnapshot = snapshot == null
                ? GovernancePolicySnapshot.from(GovernancePolicy.allowAll())
                : snapshot;
        GovernancePolicy.Builder builder = GovernancePolicy.builder()
                .deadline(safeSnapshot.deadline().orElse(null))
                .maxRequestsPerWindow(safeSnapshot.maxRequestsPerWindow())
                .rateLimitWindow(safeSnapshot.rateLimitWindow())
                .maxConcurrency(safeSnapshot.maxConcurrency())
                .degraded(safeSnapshot.degraded())
                .minimumRequests(safeSnapshot.minimumRequests())
                .failureRateThreshold(safeSnapshot.failureRateThreshold())
                .slowCallThreshold(safeSnapshot.slowCallThreshold())
                .slowCallDuration(safeSnapshot.slowCallDuration().orElse(null))
                .openStateDuration(safeSnapshot.openStateDuration())
                .halfOpenMaxCalls(safeSnapshot.halfOpenMaxCalls())
                .slidingWindowSize(safeSnapshot.slidingWindowSize())
                .slidingWindowDuration(safeSnapshot.slidingWindowDuration())
                .consecutiveFailureThreshold(safeSnapshot.consecutiveFailureThreshold());
        return builder.build();
    }

    private static final class State {
        private final String stateKey;
        private final String resourceKey;
        private final GovernanceResource.ResourceKind kind;
        private final String name;
        private final String provider;
        private final String operation;
        private final String priority;
        private final AtomicInteger activeConcurrency = new AtomicInteger();
        private final ArrayDeque<CallRecord> calls = new ArrayDeque<>();
        private GovernanceCircuitState circuitState = GovernanceCircuitState.CLOSED;
        private long totalRejections;
        private GovernanceRejectionReason lastRejectionReason = GovernanceRejectionReason.NONE;
        private GovernanceBlockReason lastBlockReason = GovernanceBlockReason.NONE;
        private CancellationReason lastCancellationReason = CancellationReason.NONE;
        private Instant lastStateTransitionAt = Instant.now();
        private GovernanceCallOutcome lastOutcome = GovernanceCallOutcome.NONE;
        private Instant lastOutcomeAt;
        private GovernancePolicySnapshot lastPolicySnapshot;

        private State(String stateKey, GovernanceResource resource, String priority, GovernancePolicy policy) {
            GovernanceResource safeResource = resource == null ? GovernanceResource.custom("unknown", "default") : resource;
            this.stateKey = stateKey;
            this.resourceKey = safeResource.key();
            this.kind = safeResource.kind();
            this.name = safeResource.name();
            this.provider = safeResource.provider();
            this.operation = safeResource.operation();
            this.priority = priority == null ? "normal" : priority;
            this.lastPolicySnapshot = GovernancePolicySnapshot.from(policy);
        }

        private synchronized void touchPolicy(GovernancePolicy policy) {
            this.lastPolicySnapshot = GovernancePolicySnapshot.from(policy);
            pruneCalls(System.nanoTime());
        }

        private synchronized void recordCall(
                GovernancePolicy policy,
                long startedNanos,
                long endedNanos,
                boolean failed,
                CancellationReason cancellationReason) {
            boolean slow = policy.slowCallDuration()
                    .map(duration -> endedNanos - startedNanos >= duration.toNanos())
                    .orElse(false);
            calls.addLast(new CallRecord(endedNanos, failed, slow));
            pruneCalls(endedNanos);
            if (cancellationReason != CancellationReason.NONE) {
                recordCancellation(cancellationReason);
            } else {
                lastOutcome = failed ? GovernanceCallOutcome.FAILURE : GovernanceCallOutcome.SUCCESS;
                lastOutcomeAt = Instant.now();
            }
        }

        private synchronized void recordBlock(
                GovernanceBlockReason blockReason,
                GovernanceRejectionReason rejectionReason) {
            recordRejection(rejectionReason, blockReason);
            if (blockReason == GovernanceBlockReason.CIRCUIT_OPEN) {
                transitionTo(GovernanceCircuitState.OPEN);
            }
        }

        private synchronized void recordRejection(
                GovernanceRejectionReason rejectionReason,
                GovernanceBlockReason blockReason) {
            totalRejections++;
            lastRejectionReason = rejectionReason == null ? GovernanceRejectionReason.NONE : rejectionReason;
            lastBlockReason = blockReason == null ? GovernanceBlockReason.NONE : blockReason;
            lastOutcome = GovernanceCallOutcome.REJECTED;
            lastOutcomeAt = Instant.now();
        }

        private synchronized void recordCancellation(CancellationReason reason) {
            lastCancellationReason = reason == null ? CancellationReason.NONE : reason;
            lastOutcome = GovernanceCallOutcome.CANCELLED;
            lastOutcomeAt = Instant.now();
        }

        private synchronized GovernanceResourceDescriptor descriptor() {
            return new GovernanceResourceDescriptor(
                    resourceKey,
                    kind,
                    name,
                    provider,
                    operation,
                    priority,
                    GovernanceEngine.SENTINEL,
                    lastPolicySnapshot,
                    snapshot());
        }

        private synchronized GovernanceRuntimeSnapshot snapshot() {
            pruneCalls(System.nanoTime());
            WindowCounts counts = windowCounts();
            GovernancePolicySnapshot policy = lastPolicySnapshot;
            return new GovernanceRuntimeSnapshot(
                    resourceKey,
                    priority,
                    GovernanceEngine.SENTINEL,
                    circuitState,
                    counts.total(),
                    counts.failures(),
                    counts.slowCalls(),
                    0,
                    totalRejections,
                    lastRejectionReason,
                    lastBlockReason,
                    lastCancellationReason,
                    null,
                    activeConcurrency.get(),
                    policy.maxConcurrency(),
                    policy.maxRequestsPerWindow(),
                    policy.rateLimitWindow(),
                    policy.degraded(),
                    policy.minimumRequests(),
                    policy.failureRateThreshold(),
                    policy.slowCallThreshold(),
                    policy.slowCallDuration().orElse(null),
                    policy.openStateDuration(),
                    policy.halfOpenMaxCalls(),
                    policy.slidingWindowSize(),
                    policy.slidingWindowDuration(),
                    policy.consecutiveFailureThreshold(),
                    lastStateTransitionAt,
                    lastOutcome,
                    lastOutcomeAt);
        }

        private synchronized GovernanceCircuitState currentCircuitState() {
            return circuitState;
        }

        private void transitionTo(GovernanceCircuitState next) {
            if (circuitState != next) {
                circuitState = next;
                lastStateTransitionAt = Instant.now();
            }
        }

        private void pruneCalls(long nowNanos) {
            long windowNanos = lastPolicySnapshot.slidingWindowDuration().toNanos();
            while (!calls.isEmpty()) {
                CallRecord oldest = calls.peekFirst();
                boolean tooOld = nowNanos - oldest.endedNanos > windowNanos;
                boolean tooMany = calls.size() > lastPolicySnapshot.slidingWindowSize();
                if (!tooOld && !tooMany) {
                    break;
                }
                calls.removeFirst();
            }
        }

        private WindowCounts windowCounts() {
            int failures = 0;
            int slowCalls = 0;
            for (CallRecord call : calls) {
                if (call.failed) {
                    failures++;
                }
                if (call.slow) {
                    slowCalls++;
                }
            }
            return new WindowCounts(calls.size(), failures, slowCalls);
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
