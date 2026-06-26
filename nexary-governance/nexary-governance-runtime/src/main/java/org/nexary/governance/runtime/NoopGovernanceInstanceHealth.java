package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.List;

/** No-op instance health diagnostics used when the feature is disabled. */
final class NoopGovernanceInstanceHealth implements GovernanceInstanceHealth {
    static final NoopGovernanceInstanceHealth INSTANCE = new NoopGovernanceInstanceHealth();

    private NoopGovernanceInstanceHealth() {}

    @Override
    public void record(InstanceHealthSignal signal) {
        // Intentionally empty.
    }

    @Override
    public List<InstanceHealthSnapshot> snapshots() {
        return Collections.emptyList();
    }

    @Override
    public InstanceHealthSummary summary() {
        return InstanceHealthSummary.empty();
    }
}
