package org.nexary.core.governance;

import java.util.concurrent.Callable;

/**
 * Executes protected work with a governance context.
 *
 * <p>This small adapter lets middleware API modules integrate with a governance runtime
 * without depending on one concrete runtime implementation.</p>
 */
public interface GovernanceExecution {
    /** Executes the action with the supplied governance context. */
    Object execute(GovernanceContext context, Callable<?> action) throws Exception;

    /** Returns a direct executor that only binds {@link GovernanceContext} and deadline context. */
    static GovernanceExecution direct() {
        return DirectGovernanceExecution.INSTANCE;
    }

    /** Direct executor used when no runtime bean is installed. */
    final class DirectGovernanceExecution implements GovernanceExecution {
        private static final DirectGovernanceExecution INSTANCE = new DirectGovernanceExecution();

        private DirectGovernanceExecution() {
        }

        @Override
        public Object execute(GovernanceContext context, Callable<?> action) throws Exception {
            return GovernanceContext.callWithContext(context, action);
        }
    }
}
