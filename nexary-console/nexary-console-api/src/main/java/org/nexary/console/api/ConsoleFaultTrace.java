package org.nexary.console.api;

import java.util.Collections;
import java.util.List;

/** Read-only console view of one retained local fault trace. */
public final class ConsoleFaultTrace {
    private final String traceKey;
    private final String rootResourceKey;
    private final String startedAt;
    private final String lastEventAt;
    private final String terminalOutcome;
    private final String primaryStopReason;
    private final String suggestedResourceKey;
    private final List<ConsoleTraceStep> steps;

    /** Creates a trace response from bounded diagnostic fields. */
    public ConsoleFaultTrace(
            String traceKey,
            String rootResourceKey,
            String startedAt,
            String lastEventAt,
            String terminalOutcome,
            String primaryStopReason,
            String suggestedResourceKey,
            List<ConsoleTraceStep> steps) {
        this.traceKey = traceKey;
        this.rootResourceKey = rootResourceKey;
        this.startedAt = startedAt;
        this.lastEventAt = lastEventAt;
        this.terminalOutcome = terminalOutcome;
        this.primaryStopReason = primaryStopReason;
        this.suggestedResourceKey = suggestedResourceKey;
        this.steps = steps == null ? Collections.emptyList() : Collections.unmodifiableList(steps);
    }

    /** Returns the local diagnostic trace key. */
    public String getTraceKey() {
        return traceKey;
    }

    /** Returns the root governed resource key. */
    public String getRootResourceKey() {
        return rootResourceKey;
    }

    /** Returns when the trace started. */
    public String getStartedAt() {
        return startedAt;
    }

    /** Returns when the latest retained step was recorded. */
    public String getLastEventAt() {
        return lastEventAt;
    }

    /** Returns the terminal trace outcome. */
    public String getTerminalOutcome() {
        return terminalOutcome;
    }

    /** Returns the primary low-cardinality stop reason. */
    public String getPrimaryStopReason() {
        return primaryStopReason;
    }

    /** Returns the resource users should inspect first. */
    public String getSuggestedResourceKey() {
        return suggestedResourceKey;
    }

    /** Returns retained trace steps. */
    public List<ConsoleTraceStep> getSteps() {
        return steps;
    }
}
