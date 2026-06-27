package org.nexary.governance.platform;

/** Fixed platform signal types. They intentionally avoid business identifiers. */
public enum GovernanceSignalType {
    REQUEST_RATE,
    ERROR_RATE,
    LATENCY,
    ACTIVE_REQUESTS,
    RETRY_STOPPED,
    CANCELLATION,
    SENTINEL_BLOCK,
    GATEWAY_DISCONNECT,
    INSTANCE_SUSPECT,
    QUARANTINE_CANDIDATE,
    RESOURCE_EVENT
}
