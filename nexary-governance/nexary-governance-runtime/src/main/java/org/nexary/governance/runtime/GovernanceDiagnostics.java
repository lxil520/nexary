package org.nexary.governance.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    /** Returns retained local fault traces, when trace diagnostics are enabled. */
    default List<GovernanceFaultTrace> traces() {
        return Collections.emptyList();
    }

    /** Returns one retained local fault trace by trace key, when enabled. */
    default Optional<GovernanceFaultTrace> trace(String traceKey) {
        return Optional.empty();
    }

    /** Returns aggregate local fault trace counters, when enabled. */
    default GovernanceFaultTraceSummary faultTraceSummary() {
        return GovernanceFaultTraceSummary.empty();
    }
}
