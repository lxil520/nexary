package org.nexary.governance.runtime;

import java.time.Instant;
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
        GovernanceDecision rejected = rejectBeforeAcquire(effectiveContext, policy);
        if (rejected != null) {
            publishRejection(effectiveContext, rejected, startedAt);
            return fallbackOrThrow(fallback, rejected);
        }
        State state = stateFor(effectiveContext, policy);
        if (!state.tryAcquire()) {
            GovernanceDecision decision = GovernanceDecision.rejected(
                    GovernanceDecision.Decision.CONCURRENCY_LIMITED,
                    new FaultSignal(FaultSignal.FaultType.REJECTED, "governance", "concurrency limited", Instant.now(), true));
            observationPublisher.publish(GovernanceObservationEvents.bulkheadRejected(
                    effectiveContext.resource(), effectiveContext.trafficTag(), startedAt, Instant.now()));
            publishRetryStopped(effectiveContext, decision, startedAt);
            return fallbackOrThrow(fallback, decision);
        }
        try {
            return GovernanceContext.callWithContext(effectiveContext, action);
        } finally {
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

    private GovernanceDecision rejectBeforeAcquire(GovernanceContext context, GovernancePolicy policy) {
        if (context.deadlineExpired()) {
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.DEADLINE_EXPIRED,
                    new FaultSignal(FaultSignal.FaultType.TIMEOUT, "governance", "deadline expired", Instant.now(), false));
        }
        if (policy.degraded()) {
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.DEGRADED,
                    new FaultSignal(FaultSignal.FaultType.DEGRADED, "governance", "degraded", Instant.now(), false));
        }
        if (!stateFor(context, policy).allowRate(policy)) {
            return GovernanceDecision.rejected(
                    GovernanceDecision.Decision.RATE_LIMITED,
                    new FaultSignal(FaultSignal.FaultType.RATE_LIMITED, "governance", "rate limited", Instant.now(), true));
        }
        return null;
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

    private State stateFor(GovernanceContext context, GovernancePolicy policy) {
        String key = context.resource().key() + ":" + context.priority().name().toLowerCase();
        State current = states.get(key);
        if (current != null && current.maxConcurrency == policy.maxConcurrency()) {
            return current;
        }
        State created = new State(policy.maxConcurrency());
        State raced = states.putIfAbsent(key, created);
        return raced == null || raced.maxConcurrency != policy.maxConcurrency() ? created : raced;
    }

    private static final class State {
        private final int maxConcurrency;
        private final Semaphore semaphore;
        private long windowStartedNanos = System.nanoTime();
        private int used;

        private State(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            this.semaphore = new Semaphore(maxConcurrency);
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
    }
}
