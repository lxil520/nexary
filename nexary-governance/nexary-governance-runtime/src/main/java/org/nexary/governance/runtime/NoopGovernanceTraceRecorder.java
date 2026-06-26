package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** No-op local fault trace recorder. */
enum NoopGovernanceTraceRecorder implements GovernanceTraceRecorder {
    /** Singleton no-op recorder. */
    INSTANCE;

    @Override
    public String start(String rootResourceKey) {
        return "trace-disabled";
    }

    @Override
    public void record(String traceKey, GovernanceTraceStep step) {
        // no-op
    }

    @Override
    public void finish(String traceKey, GovernanceCallOutcome terminalOutcome) {
        // no-op
    }

    @Override
    public List<GovernanceFaultTrace> traces() {
        return Collections.emptyList();
    }

    @Override
    public Optional<GovernanceFaultTrace> trace(String traceKey) {
        return Optional.empty();
    }

    @Override
    public GovernanceFaultTraceSummary summary() {
        return GovernanceFaultTraceSummary.empty();
    }
}
