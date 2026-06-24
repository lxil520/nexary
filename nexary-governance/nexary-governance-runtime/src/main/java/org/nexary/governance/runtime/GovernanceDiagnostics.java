package org.nexary.governance.runtime;

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
}
