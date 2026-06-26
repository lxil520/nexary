package org.nexary.governance.runtime;

import java.util.List;
import java.util.Optional;

/** Records and exposes bounded local fault traces for read-only diagnostics. */
public interface GovernanceTraceRecorder {
    /** Starts a local trace for a governed root resource and returns the local trace key. */
    String start(String rootResourceKey);

    /** Records one bounded low-cardinality trace step. */
    void record(String traceKey, GovernanceTraceStep step);

    /** Finishes a trace with its terminal outcome. */
    void finish(String traceKey, GovernanceCallOutcome terminalOutcome);

    /** Returns retained fault traces in oldest-to-newest order. */
    List<GovernanceFaultTrace> traces();

    /** Returns one retained trace by local trace key. */
    Optional<GovernanceFaultTrace> trace(String traceKey);

    /** Returns aggregate local trace counters. */
    GovernanceFaultTraceSummary summary();

    /** Returns a no-op trace recorder. */
    static GovernanceTraceRecorder noop() {
        return NoopGovernanceTraceRecorder.INSTANCE;
    }
}
