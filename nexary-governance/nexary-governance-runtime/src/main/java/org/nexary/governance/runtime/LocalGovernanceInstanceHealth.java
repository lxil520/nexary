package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory detector for local instance health diagnostics. */
public final class LocalGovernanceInstanceHealth implements GovernanceInstanceHealth {
    private final InstanceHealthSettings settings;
    private final Map<Key, State> states = new ConcurrentHashMap<>();
    private final ArrayDeque<GovernanceRuntimeEvent> recentEvents = new ArrayDeque<>();
    private final int recentEventCapacity;

    /** Creates a detector with default settings. */
    public LocalGovernanceInstanceHealth() {
        this(new InstanceHealthSettings());
    }

    /** Creates a detector with explicit settings. */
    public LocalGovernanceInstanceHealth(InstanceHealthSettings settings) {
        this(settings, 256);
    }

    /** Creates a detector with explicit settings and event capacity. */
    public LocalGovernanceInstanceHealth(InstanceHealthSettings settings, int recentEventCapacity) {
        this.settings = settings == null ? new InstanceHealthSettings() : settings;
        this.recentEventCapacity = Math.max(1, recentEventCapacity);
    }

    @Override
    public void record(InstanceHealthSignal signal) {
        if (signal == null) {
            return;
        }
        Key key = new Key(signal.instanceRef());
        State state = states.computeIfAbsent(key, ignored -> new State(signal.instanceRef()));
        synchronized (state) {
            state.record(signal, settings.window());
        }
        evaluateResource(signal.instanceRef().resourceKey(), signal.timestamp());
    }

    @Override
    public List<InstanceHealthSnapshot> snapshots() {
        List<InstanceHealthSnapshot> snapshots = new ArrayList<>();
        for (State state : states.values()) {
            synchronized (state) {
                state.prune(Instant.now(), settings.window());
                snapshots.add(state.snapshot());
            }
        }
        snapshots.sort(Comparator
                .comparing((InstanceHealthSnapshot snapshot) -> snapshot.instanceRef().resourceKey())
                .thenComparing(snapshot -> snapshot.instanceRef().instanceKey()));
        return snapshots;
    }

    @Override
    public InstanceHealthSummary summary() {
        long healthy = 0L;
        long suspect = 0L;
        long quarantine = 0L;
        long recovering = 0L;
        long recoveryProbe = 0L;
        List<InstanceHealthSnapshot> current = snapshots();
        for (InstanceHealthSnapshot snapshot : current) {
            if (snapshot.state() == InstanceHealthState.HEALTHY) {
                healthy++;
            } else if (snapshot.state() == InstanceHealthState.SUSPECT) {
                suspect++;
            } else if (snapshot.state() == InstanceHealthState.QUARANTINE_CANDIDATE) {
                quarantine++;
            } else if (snapshot.state() == InstanceHealthState.RECOVERING) {
                recovering++;
            }
            if (snapshot.recoveryAdvice() == InstanceRecoveryAdvice.RECOVERY_PROBE) {
                recoveryProbe++;
            }
        }
        return new InstanceHealthSummary(current.size(), healthy, suspect, quarantine, recovering, recoveryProbe);
    }

    @Override
    public List<GovernanceRuntimeEvent> recentEvents() {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents);
        }
    }

    private void evaluateResource(String resourceKey, Instant now) {
        List<State> resourceStates = new ArrayList<>();
        for (State state : states.values()) {
            if (state.instanceRef.resourceKey().equals(resourceKey)) {
                synchronized (state) {
                    state.prune(now, settings.window());
                    resourceStates.add(state.copyForEvaluation());
                }
            }
        }
        Baseline baseline = Baseline.from(resourceStates);
        for (State state : resourceStates) {
            Evaluation evaluation = state.evaluate(settings, baseline);
            State actual = states.get(new Key(state.instanceRef));
            if (actual != null) {
                synchronized (actual) {
                    InstanceHealthState previous = actual.state;
                    actual.apply(evaluation, now, settings);
                    if (previous != actual.state) {
                        recordStateEvent(actual, now);
                    }
                }
            }
        }
    }

    private void recordStateEvent(State state, Instant now) {
        GovernanceRuntimeAction action;
        if (state.state == InstanceHealthState.SUSPECT) {
            action = GovernanceRuntimeAction.INSTANCE_SUSPECT;
        } else if (state.state == InstanceHealthState.QUARANTINE_CANDIDATE) {
            action = GovernanceRuntimeAction.QUARANTINE_CANDIDATE;
        } else if (state.state == InstanceHealthState.RECOVERING) {
            action = GovernanceRuntimeAction.RECOVERY_PROBE;
        } else {
            action = GovernanceRuntimeAction.INSTANCE_RECOVERED;
        }
        GovernanceRuntimeEvent event = new GovernanceRuntimeEvent(
                state.instanceRef.resourceKey(),
                action,
                GovernanceCallOutcome.NONE,
                GovernanceRejectionReason.NONE,
                GovernanceCircuitState.CLOSED,
                now,
                GovernanceDurationBucket.NOT_RUN,
                state.state,
                state.reason,
                state.advice);
        synchronized (recentEvents) {
            while (recentEvents.size() >= recentEventCapacity) {
                recentEvents.removeFirst();
            }
            recentEvents.addLast(event);
        }
    }

    private static double ratio(int count, int total) {
        if (total <= 0) {
            return 0.0d;
        }
        return (double) count / (double) total;
    }

    private static final class Key {
        private final String resourceKey;
        private final String instanceKey;

        private Key(GovernanceInstanceRef ref) {
            this.resourceKey = ref.resourceKey();
            this.instanceKey = ref.instanceKey();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }
            Key that = (Key) other;
            return resourceKey.equals(that.resourceKey) && instanceKey.equals(that.instanceKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resourceKey, instanceKey);
        }
    }

    private static final class State {
        private final GovernanceInstanceRef instanceRef;
        private final ArrayDeque<InstanceHealthSignal> signals = new ArrayDeque<>();
        private InstanceHealthState state = InstanceHealthState.HEALTHY;
        private InstanceQuarantineReason reason = InstanceQuarantineReason.NONE;
        private InstanceRecoveryAdvice advice = InstanceRecoveryAdvice.NONE;
        private int suspectStreak;
        private int recoveryStreak;
        private double lastSkewFactor;
        private Instant lastSignalAt;
        private Instant lastChangedAt;

        private State(GovernanceInstanceRef instanceRef) {
            this.instanceRef = instanceRef;
        }

        private void record(InstanceHealthSignal signal, java.time.Duration window) {
            signals.addLast(signal);
            lastSignalAt = signal.timestamp();
            prune(signal.timestamp(), window);
        }

        private void prune(Instant now, java.time.Duration window) {
            Instant cutoff = now.minus(window);
            while (!signals.isEmpty() && signals.peekFirst().timestamp().isBefore(cutoff)) {
                signals.removeFirst();
            }
        }

        private State copyForEvaluation() {
            State copy = new State(instanceRef);
            copy.signals.addAll(signals);
            copy.state = state;
            copy.reason = reason;
            copy.advice = advice;
            copy.suspectStreak = suspectStreak;
            copy.recoveryStreak = recoveryStreak;
            copy.lastSkewFactor = lastSkewFactor;
            copy.lastSignalAt = lastSignalAt;
            copy.lastChangedAt = lastChangedAt;
            return copy;
        }

        private Evaluation evaluate(InstanceHealthSettings settings, Baseline baseline) {
            Counters counters = Counters.from(signals);
            if (counters.calls < settings.minimumCalls()) {
                return Evaluation.healthy(counters, 0.0d);
            }
            double failureRatio = ratio(counters.failures, counters.calls);
            double slowRatio = ratio(counters.slowCalls, counters.calls);
            double timeoutRatio = ratio(counters.timeouts + counters.resets, counters.calls);
            double serverErrorRatio = ratio(counters.serverErrors, counters.calls);
            double skew = baseline.skewFor(instanceRef.instanceKey(), failureRatio, slowRatio, timeoutRatio, serverErrorRatio);
            if (timeoutRatio >= settings.timeoutRatioThreshold()) {
                return Evaluation.abnormal(counters, timeoutReason(counters), skew);
            }
            if (serverErrorRatio >= settings.failureRatioThreshold()) {
                return Evaluation.abnormal(counters, InstanceQuarantineReason.SERVER_ERROR_RATIO, skew);
            }
            if (failureRatio >= settings.failureRatioThreshold()) {
                return Evaluation.abnormal(counters, InstanceQuarantineReason.SERVER_ERROR_RATIO, skew);
            }
            if (slowRatio >= settings.slowRatioThreshold()) {
                return Evaluation.abnormal(counters, InstanceQuarantineReason.SLOW_RATIO, skew);
            }
            if (skew >= settings.skewFactorThreshold()) {
                return Evaluation.abnormal(counters, InstanceQuarantineReason.STATUS_CODE_SKEW, skew);
            }
            return Evaluation.healthy(counters, skew);
        }

        private void apply(Evaluation evaluation, Instant now, InstanceHealthSettings settings) {
            lastSkewFactor = evaluation.skewFactor;
            if (evaluation.abnormal) {
                suspectStreak++;
                recoveryStreak = 0;
                reason = evaluation.reason;
                if (suspectStreak >= settings.suspectWindows()) {
                    changeState(InstanceHealthState.QUARANTINE_CANDIDATE, now);
                    advice = InstanceRecoveryAdvice.QUARANTINE_CANDIDATE;
                } else {
                    changeState(InstanceHealthState.SUSPECT, now);
                    advice = InstanceRecoveryAdvice.BACKOFF;
                }
                return;
            }
            suspectStreak = 0;
            if (state == InstanceHealthState.SUSPECT || state == InstanceHealthState.QUARANTINE_CANDIDATE) {
                recoveryStreak++;
                reason = InstanceQuarantineReason.NONE;
                if (recoveryStreak >= settings.recoveryWindows()) {
                    changeState(InstanceHealthState.HEALTHY, now);
                    advice = InstanceRecoveryAdvice.NONE;
                } else {
                    changeState(InstanceHealthState.RECOVERING, now);
                    advice = InstanceRecoveryAdvice.RECOVERY_PROBE;
                }
            } else {
                reason = InstanceQuarantineReason.NONE;
                advice = InstanceRecoveryAdvice.NONE;
                changeState(InstanceHealthState.HEALTHY, now);
            }
        }

        private void changeState(InstanceHealthState next, Instant now) {
            if (state != next) {
                state = next;
                lastChangedAt = now;
            }
        }

        private InstanceHealthSnapshot snapshot() {
            Counters counters = Counters.from(signals);
            return new InstanceHealthSnapshot(
                    instanceRef,
                    state,
                    reason,
                    advice,
                    counters.calls,
                    counters.failures,
                    counters.slowCalls,
                    counters.timeouts,
                    counters.resets,
                    counters.serverErrors,
                    ratio(counters.failures, counters.calls),
                    ratio(counters.slowCalls, counters.calls),
                    ratio(counters.timeouts + counters.resets, counters.calls),
                    lastSkewFactor,
                    lastSignalAt,
                    lastChangedAt);
        }

        private InstanceQuarantineReason timeoutReason(Counters counters) {
            if (counters.resets > counters.connectTimeouts && counters.resets > counters.readTimeouts) {
                return InstanceQuarantineReason.RESET_SPIKE;
            }
            if (counters.connectTimeouts >= counters.readTimeouts && counters.connectTimeouts > 0) {
                return InstanceQuarantineReason.CONNECT_TIMEOUT_SPIKE;
            }
            return InstanceQuarantineReason.READ_TIMEOUT_SPIKE;
        }
    }

    private static final class Counters {
        private final int calls;
        private final int failures;
        private final int slowCalls;
        private final int timeouts;
        private final int connectTimeouts;
        private final int readTimeouts;
        private final int resets;
        private final int serverErrors;

        private Counters(
                int calls,
                int failures,
                int slowCalls,
                int timeouts,
                int connectTimeouts,
                int readTimeouts,
                int resets,
                int serverErrors) {
            this.calls = calls;
            this.failures = failures;
            this.slowCalls = slowCalls;
            this.timeouts = timeouts;
            this.connectTimeouts = connectTimeouts;
            this.readTimeouts = readTimeouts;
            this.resets = resets;
            this.serverErrors = serverErrors;
        }

        private static Counters from(Iterable<InstanceHealthSignal> signals) {
            int calls = 0;
            int failures = 0;
            int slowCalls = 0;
            int timeouts = 0;
            int connectTimeouts = 0;
            int readTimeouts = 0;
            int resets = 0;
            int serverErrors = 0;
            for (InstanceHealthSignal signal : signals) {
                calls++;
                boolean failed = signal.outcome() == GovernanceCallOutcome.FAILURE;
                if (signal.signalType() == InstanceHealthSignalType.SLOW_CALL
                        || signal.durationBucket() == GovernanceDurationBucket.GE_500_MS) {
                    slowCalls++;
                }
                if (signal.signalType() == InstanceHealthSignalType.CONNECT_TIMEOUT) {
                    timeouts++;
                    connectTimeouts++;
                    failed = true;
                }
                if (signal.signalType() == InstanceHealthSignalType.READ_TIMEOUT) {
                    timeouts++;
                    readTimeouts++;
                    failed = true;
                }
                if (signal.signalType() == InstanceHealthSignalType.CONNECTION_RESET) {
                    resets++;
                    failed = true;
                }
                if (signal.signalType() == InstanceHealthSignalType.SERVER_ERROR
                        || signal.statusCodeClass() == InstanceStatusCodeClass.HTTP_5XX) {
                    serverErrors++;
                    failed = true;
                }
                if (failed) {
                    failures++;
                }
            }
            return new Counters(calls, failures, slowCalls, timeouts, connectTimeouts, readTimeouts, resets, serverErrors);
        }
    }

    private static final class Evaluation {
        private final Counters counters;
        private final boolean abnormal;
        private final InstanceQuarantineReason reason;
        private final double skewFactor;

        private Evaluation(Counters counters, boolean abnormal, InstanceQuarantineReason reason, double skewFactor) {
            this.counters = counters;
            this.abnormal = abnormal;
            this.reason = reason;
            this.skewFactor = skewFactor;
        }

        private static Evaluation healthy(Counters counters, double skewFactor) {
            return new Evaluation(counters, false, InstanceQuarantineReason.NONE, skewFactor);
        }

        private static Evaluation abnormal(
                Counters counters,
                InstanceQuarantineReason reason,
                double skewFactor) {
            return new Evaluation(counters, true, reason, skewFactor);
        }
    }

    private static final class Baseline {
        private final Map<String, Ratios> ratiosByInstance;

        private Baseline(Map<String, Ratios> ratiosByInstance) {
            this.ratiosByInstance = ratiosByInstance;
        }

        private static Baseline from(List<State> states) {
            Map<String, Ratios> ratios = new LinkedHashMap<>();
            for (State state : states) {
                Counters counters = Counters.from(state.signals);
                ratios.put(state.instanceRef.instanceKey(), new Ratios(
                        ratio(counters.failures, counters.calls),
                        ratio(counters.slowCalls, counters.calls),
                        ratio(counters.timeouts + counters.resets, counters.calls),
                        ratio(counters.serverErrors, counters.calls)));
            }
            return new Baseline(ratios);
        }

        private double skewFor(
                String instanceKey,
                double failureRatio,
                double slowRatio,
                double timeoutRatio,
                double serverErrorRatio) {
            if (ratiosByInstance.size() <= 1) {
                return 0.0d;
            }
            Ratios peer = peerAverage(instanceKey);
            return Math.max(
                    Math.max(skew(failureRatio, peer.failureRatio), skew(slowRatio, peer.slowRatio)),
                    Math.max(skew(timeoutRatio, peer.timeoutRatio), skew(serverErrorRatio, peer.serverErrorRatio)));
        }

        private Ratios peerAverage(String instanceKey) {
            int count = 0;
            double failure = 0.0d;
            double slow = 0.0d;
            double timeout = 0.0d;
            double serverError = 0.0d;
            for (Map.Entry<String, Ratios> entry : ratiosByInstance.entrySet()) {
                if (entry.getKey().equals(instanceKey)) {
                    continue;
                }
                count++;
                failure += entry.getValue().failureRatio;
                slow += entry.getValue().slowRatio;
                timeout += entry.getValue().timeoutRatio;
                serverError += entry.getValue().serverErrorRatio;
            }
            if (count == 0) {
                return new Ratios(0.0d, 0.0d, 0.0d, 0.0d);
            }
            return new Ratios(failure / count, slow / count, timeout / count, serverError / count);
        }

        private double skew(double current, double baseline) {
            if (current <= 0.0d) {
                return 0.0d;
            }
            return current / Math.max(0.01d, baseline);
        }
    }

    private static final class Ratios {
        private final double failureRatio;
        private final double slowRatio;
        private final double timeoutRatio;
        private final double serverErrorRatio;

        private Ratios(double failureRatio, double slowRatio, double timeoutRatio, double serverErrorRatio) {
            this.failureRatio = failureRatio;
            this.slowRatio = slowRatio;
            this.timeoutRatio = timeoutRatio;
            this.serverErrorRatio = serverErrorRatio;
        }
    }
}
