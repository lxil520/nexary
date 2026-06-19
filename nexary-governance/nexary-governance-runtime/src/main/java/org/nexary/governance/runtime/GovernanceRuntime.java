package org.nexary.governance.runtime;

import java.util.concurrent.Callable;
import org.nexary.core.governance.GovernanceContext;

/** Runs application code through local in-process governance policies. */
public interface GovernanceRuntime {
    /** Executes the action or throws GovernanceRejectedException when rejected. */
    <T> T execute(GovernanceContext context, Callable<T> action) throws Exception;

    /** Executes the action, or the fallback when local governance rejects it. */
    <T> T execute(GovernanceContext context, Callable<T> action, Callable<T> fallback) throws Exception;
}
