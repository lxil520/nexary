package org.nexary.governance.runtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Read-only local diagnostic trace for one governed call or fault signal. */
public final class GovernanceFaultTrace {
    private final String traceKey;
    private final String rootResourceKey;
    private final Instant startedAt;
    private final Instant lastEventAt;
    private final GovernanceCallOutcome terminalOutcome;
    private final GovernanceTraceStopReason primaryStopReason;
    private final String suggestedResourceKey;
    private final List<GovernanceTraceStep> steps;

    /** Creates a local fault trace snapshot. */
    public GovernanceFaultTrace(
            String traceKey,
            String rootResourceKey,
            Instant startedAt,
            Instant lastEventAt,
            GovernanceCallOutcome terminalOutcome,
            GovernanceTraceStopReason primaryStopReason,
            String suggestedResourceKey,
            List<GovernanceTraceStep> steps) {
        this.traceKey = traceKey == null ? "trace-unknown" : traceKey;
        this.rootResourceKey = rootResourceKey == null ? "custom:unknown:unknown:default" : rootResourceKey;
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
        this.lastEventAt = lastEventAt == null ? this.startedAt : lastEventAt;
        this.terminalOutcome = terminalOutcome == null ? GovernanceCallOutcome.NONE : terminalOutcome;
        this.primaryStopReason = primaryStopReason == null ? GovernanceTraceStopReason.NONE : primaryStopReason;
        this.suggestedResourceKey = suggestedResourceKey == null ? this.rootResourceKey : suggestedResourceKey;
        this.steps = immutableSteps(steps);
    }

    /** Returns the local diagnostic trace key. */
    public String traceKey() {
        return traceKey;
    }

    /** Returns the root governed resource key for this trace. */
    public String rootResourceKey() {
        return rootResourceKey;
    }

    /** Returns when this local trace started. */
    public Instant startedAt() {
        return startedAt;
    }

    /** Returns when the latest retained step was recorded. */
    public Instant lastEventAt() {
        return lastEventAt;
    }

    /** Returns the terminal call outcome. */
    public GovernanceCallOutcome terminalOutcome() {
        return terminalOutcome;
    }

    /** Returns the primary low-cardinality stop reason. */
    public GovernanceTraceStopReason primaryStopReason() {
        return primaryStopReason;
    }

    /** Returns the resource a user should inspect first. */
    public String suggestedResourceKey() {
        return suggestedResourceKey;
    }

    /** Returns trace steps in oldest-to-newest order. */
    public List<GovernanceTraceStep> steps() {
        return steps;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GovernanceFaultTrace)) {
            return false;
        }
        GovernanceFaultTrace that = (GovernanceFaultTrace) other;
        return traceKey.equals(that.traceKey)
                && rootResourceKey.equals(that.rootResourceKey)
                && startedAt.equals(that.startedAt)
                && lastEventAt.equals(that.lastEventAt)
                && terminalOutcome == that.terminalOutcome
                && primaryStopReason == that.primaryStopReason
                && suggestedResourceKey.equals(that.suggestedResourceKey)
                && steps.equals(that.steps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                traceKey,
                rootResourceKey,
                startedAt,
                lastEventAt,
                terminalOutcome,
                primaryStopReason,
                suggestedResourceKey,
                steps);
    }

    private static List<GovernanceTraceStep> immutableSteps(List<GovernanceTraceStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(steps));
    }
}
