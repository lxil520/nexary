package org.nexary.governance.runtime;

/** Circuit-breaker state for a locally governed resource. */
public enum GovernanceCircuitState {
    /** Calls run normally and completed calls are recorded in the sliding window. */
    CLOSED,

    /** Calls are rejected until the configured open-state duration expires. */
    OPEN,

    /** A small number of probe calls are allowed to decide whether the resource has recovered. */
    HALF_OPEN
}
