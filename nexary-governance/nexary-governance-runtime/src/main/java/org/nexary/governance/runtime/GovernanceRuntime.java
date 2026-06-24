package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.nexary.core.governance.GovernanceContext;

/** Runs application code through local in-process governance policies. */
public interface GovernanceRuntime extends GovernanceDiagnostics {
    /** Executes the action or throws GovernanceRejectedException when rejected. */
    <T> T execute(GovernanceContext context, Callable<T> action) throws Exception;

    /** Executes the action, or the fallback when local governance rejects it. */
    <T> T execute(GovernanceContext context, Callable<T> action, Callable<T> fallback) throws Exception;

    @Override
    default List<GovernanceResourceDescriptor> resources() {
        return Collections.emptyList();
    }

    @Override
    default List<GovernanceRuntimeSnapshot> snapshots() {
        return Collections.emptyList();
    }

    @Override
    default List<GovernanceRuntimeEvent> recentEvents() {
        return Collections.emptyList();
    }

    @Override
    default GovernanceRuntimeSummary summary() {
        return new GovernanceRuntimeSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, null);
    }
}
