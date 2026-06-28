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
    REDIS_TIMEOUT,
    REDIS_PIPELINE_ERROR,
    BROKEN_PIPE,
    DEPENDENCY_TIMEOUT,
    NETWORK_JITTER,
    PACKET_LOSS,
    HOST_WATERMARK,
    RESOURCE_EVENT
}
