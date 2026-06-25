package org.nexary.governance.runtime;

/** Low-cardinality outcome for the most recent local governance attempt. */
public enum GovernanceCallOutcome {
    /** No attempt has completed or been rejected for this runtime state. */
    NONE,

    /** The action completed successfully. */
    SUCCESS,

    /** The action ran and completed with an error. */
    FAILURE,

    /** Local governance rejected the attempt before the action completed. */
    REJECTED,

    /** The attempt was cancelled before the action completed. */
    CANCELLED,

    /** A retry loop stopped before scheduling another attempt. */
    RETRY_STOPPED,

    /** A request was isolated by priority policy before useful work ran. */
    ISOLATED
}
