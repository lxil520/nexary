package org.nexary.governance.platform;

/** External evidence systems referenced by the read-only governance platform. */
public enum GovernanceEvidenceRefType {
    SKYWALKING_TRACE,
    CAT_TRANSACTION,
    PROMQL,
    LOG_QUERY,
    SENTINEL_RESOURCE,
    GATEWAY_ROUTE,
    SBA_INSTANCE,
    NEXARY_FAULT_TRACE
}
