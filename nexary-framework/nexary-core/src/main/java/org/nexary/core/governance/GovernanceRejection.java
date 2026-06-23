package org.nexary.core.governance;

/** Marks a failure raised because governance rejected execution before user work should continue. */
public interface GovernanceRejection {
    /** Returns a low-cardinality rejection reason. */
    String governanceRejectionReason();
}
