package org.nexary.governance.platform;

/** Local access mode allowed for a governance connector. */
public enum GovernanceConnectorAccessMode {
    /** Connector is configured for read-only evidence collection. */
    READ_ONLY,
    /** Connector may be used to calculate dry-run material only. */
    DRY_RUN,
    /** Connector may run explicitly marked local tests only. */
    TEST,
    /** Connector is locally disabled. */
    DISABLED
}
