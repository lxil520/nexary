package org.nexary.governance.platform;

/** Authentication mode for a locally configured governance connector. */
public enum GovernanceConnectorAuthMode {
    /** No authentication is required. */
    NONE,
    /** Basic authentication is configured outside normal API responses. */
    BASIC,
    /** Bearer token authentication is configured outside normal API responses. */
    BEARER_TOKEN,
    /** Webhook secret authentication is configured outside normal API responses. */
    WEBHOOK_SECRET,
    /** Actuator basic authentication is configured outside normal API responses. */
    ACTUATOR_BASIC
}
