package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.List;

/** Read-only local governance diagnostics. */
public interface GovernanceDiagnostics {
    /** Returns resource descriptors known by the local runtime. */
    List<GovernanceResourceDescriptor> resources();

    /** Returns low-cardinality snapshots for current local resource states. */
    List<GovernanceRuntimeSnapshot> snapshots();

    /** Returns recent low-cardinality runtime events in oldest-to-newest order. */
    List<GovernanceRuntimeEvent> recentEvents();

    /** Returns an aggregate view of local governance resources, snapshots, and recent events. */
    GovernanceRuntimeSummary summary();

    /** Returns local instance health snapshots, when instance health diagnostics are enabled. */
    default List<InstanceHealthSnapshot> instanceHealthSnapshots() {
        return Collections.emptyList();
    }

    /** Returns aggregate local instance health counters, when enabled. */
    default InstanceHealthSummary instanceHealthSummary() {
        return InstanceHealthSummary.empty();
    }
}
