package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.List;

/** Records and exposes read-only local instance health diagnostics. */
public interface GovernanceInstanceHealth {
    /** Records one bounded instance health signal. */
    void record(InstanceHealthSignal signal);

    /** Returns local instance health snapshots in stable order. */
    List<InstanceHealthSnapshot> snapshots();

    /** Returns local instance health snapshots for one resource key. */
    default List<InstanceHealthSnapshot> snapshots(String resourceKey) {
        if (resourceKey == null) {
            return Collections.emptyList();
        }
        return snapshots().stream()
                .filter(snapshot -> resourceKey.equals(snapshot.instanceRef().resourceKey()))
                .collect(java.util.stream.Collectors.toList());
    }

    /** Returns aggregate local instance health counters. */
    InstanceHealthSummary summary();

    /** Returns recent low-cardinality instance health events. */
    default List<GovernanceRuntimeEvent> recentEvents() {
        return Collections.emptyList();
    }

    /** Returns a no-op instance health recorder. */
    static GovernanceInstanceHealth noop() {
        return NoopGovernanceInstanceHealth.INSTANCE;
    }
}
