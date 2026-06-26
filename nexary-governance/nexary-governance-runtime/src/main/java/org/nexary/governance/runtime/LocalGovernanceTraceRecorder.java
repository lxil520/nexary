package org.nexary.governance.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/** In-memory bounded recorder for local fault traces. */
public final class LocalGovernanceTraceRecorder implements GovernanceTraceRecorder {
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    private final int maxTraces;
    private final int maxStepsPerTrace;
    private final Duration ttl;
    private final AtomicLong sequence = new AtomicLong();
    private final ArrayDeque<MutableTrace> order = new ArrayDeque<>();
    private final Map<String, MutableTrace> traces = new LinkedHashMap<>();

    /** Creates a recorder with v0.16 defaults. */
    public LocalGovernanceTraceRecorder() {
        this(128, 32, DEFAULT_TTL);
    }

    /** Creates a recorder with explicit bounds. */
    public LocalGovernanceTraceRecorder(int maxTraces, int maxStepsPerTrace, Duration ttl) {
        this.maxTraces = Math.max(1, maxTraces);
        this.maxStepsPerTrace = Math.max(1, maxStepsPerTrace);
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
    }

    @Override
    public synchronized String start(String rootResourceKey) {
        pruneExpired(Instant.now());
        String key = "trace-" + Long.toString(sequence.incrementAndGet(), 36);
        MutableTrace trace = new MutableTrace(key, normalizeResource(rootResourceKey), Instant.now());
        order.addLast(trace);
        traces.put(key, trace);
        while (order.size() > maxTraces) {
            MutableTrace oldest = order.removeFirst();
            traces.remove(oldest.traceKey);
        }
        return key;
    }

    @Override
    public synchronized void record(String traceKey, GovernanceTraceStep step) {
        if (traceKey == null || step == null) {
            return;
        }
        MutableTrace trace = traces.get(traceKey);
        if (trace == null) {
            return;
        }
        trace.steps.addLast(step);
        while (trace.steps.size() > maxStepsPerTrace) {
            trace.steps.removeFirst();
        }
        trace.lastEventAt = step.timestamp();
        trace.primaryStopReason = choosePrimary(trace.primaryStopReason, GovernanceTraceStopReason.fromStep(step));
        if (isSuggestionCandidate(step, trace.primaryStopReason)) {
            trace.suggestedResourceKey = step.resourceKey();
        }
    }

    @Override
    public synchronized void finish(String traceKey, GovernanceCallOutcome terminalOutcome) {
        MutableTrace trace = traces.get(traceKey);
        if (trace == null) {
            return;
        }
        trace.terminalOutcome = terminalOutcome == null ? GovernanceCallOutcome.NONE : terminalOutcome;
        if (trace.lastEventAt == null) {
            trace.lastEventAt = Instant.now();
        }
    }

    @Override
    public synchronized List<GovernanceFaultTrace> traces() {
        pruneExpired(Instant.now());
        List<GovernanceFaultTrace> snapshots = new ArrayList<>();
        for (MutableTrace trace : order) {
            snapshots.add(trace.snapshot());
        }
        return snapshots;
    }

    @Override
    public synchronized Optional<GovernanceFaultTrace> trace(String traceKey) {
        pruneExpired(Instant.now());
        MutableTrace trace = traces.get(traceKey);
        return trace == null ? Optional.empty() : Optional.of(trace.snapshot());
    }

    @Override
    public synchronized GovernanceFaultTraceSummary summary() {
        pruneExpired(Instant.now());
        long stopped = 0L;
        long blocked = 0L;
        long cancelled = 0L;
        long retryStopped = 0L;
        long instanceRelated = 0L;
        Map<String, Long> topStopReasons = new LinkedHashMap<>();
        for (MutableTrace trace : order) {
            GovernanceTraceStopReason reason = trace.primaryStopReason;
            if (reason != GovernanceTraceStopReason.NONE) {
                topStopReasons.merge(reason.name(), 1L, Long::sum);
            }
            if (reason != GovernanceTraceStopReason.NONE && trace.terminalOutcome != GovernanceCallOutcome.SUCCESS) {
                stopped++;
            }
            if (reason == GovernanceTraceStopReason.BLOCKED || reason == GovernanceTraceStopReason.REJECTED) {
                blocked++;
            }
            if (reason == GovernanceTraceStopReason.CANCELLED || reason == GovernanceTraceStopReason.DEADLINE_EXPIRED) {
                cancelled++;
            }
            if (reason == GovernanceTraceStopReason.RETRY_STOPPED) {
                retryStopped++;
            }
            if (reason == GovernanceTraceStopReason.INSTANCE_QUARANTINE_CANDIDATE) {
                instanceRelated++;
            }
        }
        return new GovernanceFaultTraceSummary(
                order.size(),
                stopped,
                blocked,
                cancelled,
                retryStopped,
                instanceRelated,
                topStopReasons);
    }

    private void pruneExpired(Instant now) {
        Instant cutoff = now.minus(ttl);
        while (!order.isEmpty() && order.peekFirst().startedAt.isBefore(cutoff)) {
            MutableTrace oldest = order.removeFirst();
            traces.remove(oldest.traceKey);
        }
    }

    private static String normalizeResource(String resourceKey) {
        return resourceKey == null ? "custom:unknown:unknown:default" : resourceKey;
    }

    private static GovernanceTraceStopReason choosePrimary(
            GovernanceTraceStopReason current,
            GovernanceTraceStopReason next) {
        GovernanceTraceStopReason safeCurrent = current == null ? GovernanceTraceStopReason.NONE : current;
        GovernanceTraceStopReason safeNext = next == null ? GovernanceTraceStopReason.NONE : next;
        return priority(safeNext) < priority(safeCurrent) ? safeNext : safeCurrent;
    }

    private static int priority(GovernanceTraceStopReason reason) {
        if (reason == GovernanceTraceStopReason.DEADLINE_EXPIRED || reason == GovernanceTraceStopReason.CANCELLED) {
            return 1;
        }
        if (reason == GovernanceTraceStopReason.RETRY_STOPPED) {
            return 2;
        }
        if (reason == GovernanceTraceStopReason.BLOCKED || reason == GovernanceTraceStopReason.REJECTED) {
            return 3;
        }
        if (reason == GovernanceTraceStopReason.ISOLATED) {
            return 4;
        }
        if (reason == GovernanceTraceStopReason.INSTANCE_QUARANTINE_CANDIDATE) {
            return 5;
        }
        if (reason == GovernanceTraceStopReason.FAILURE) {
            return 6;
        }
        return 100;
    }

    private static boolean isSuggestionCandidate(GovernanceTraceStep step, GovernanceTraceStopReason reason) {
        return reason != GovernanceTraceStopReason.NONE
                && step != null
                && step.resourceKey() != null;
    }

    private static final class MutableTrace {
        private final String traceKey;
        private final String rootResourceKey;
        private final Instant startedAt;
        private final ArrayDeque<GovernanceTraceStep> steps = new ArrayDeque<>();
        private Instant lastEventAt;
        private GovernanceCallOutcome terminalOutcome = GovernanceCallOutcome.NONE;
        private GovernanceTraceStopReason primaryStopReason = GovernanceTraceStopReason.NONE;
        private String suggestedResourceKey;

        private MutableTrace(String traceKey, String rootResourceKey, Instant startedAt) {
            this.traceKey = traceKey;
            this.rootResourceKey = rootResourceKey;
            this.startedAt = startedAt;
            this.lastEventAt = startedAt;
            this.suggestedResourceKey = rootResourceKey;
        }

        private GovernanceFaultTrace snapshot() {
            return new GovernanceFaultTrace(
                    traceKey,
                    rootResourceKey,
                    startedAt,
                    lastEventAt,
                    terminalOutcome,
                    primaryStopReason,
                    suggestedResourceKey,
                    new ArrayList<>(steps));
        }
    }
}
