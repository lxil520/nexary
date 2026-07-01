package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Local platform configuration for connecting to an external governance tool. */
public final class GovernanceConnectorConfig {
    private final String connectorKey;
    private final GovernanceConnectorKind kind;
    private final String displayName;
    private final String endpoint;
    private final GovernanceConnectorAuthMode authMode;
    private final GovernanceConnectorAccessMode accessMode;
    private final GovernanceConnectorState state;
    private final boolean testEnabled;
    private final List<GovernanceConnectorCapability> capabilities;
    private final String lastMessage;
    private final Map<String, String> attributes;
    private final Instant createdAt;
    private final Instant updatedAt;

    /** Creates local connector configuration. */
    public GovernanceConnectorConfig(
            String connectorKey,
            GovernanceConnectorKind kind,
            String displayName,
            String endpoint,
            GovernanceConnectorAuthMode authMode,
            GovernanceConnectorAccessMode accessMode,
            GovernanceConnectorState state,
            boolean testEnabled,
            List<GovernanceConnectorCapability> capabilities,
            String lastMessage,
            Map<String, String> attributes,
            Instant createdAt,
            Instant updatedAt) {
        this.connectorKey = GovernancePlatformValidators.token(connectorKey, "connectorKey");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.displayName = GovernancePlatformValidators.label(displayName, "displayName");
        this.endpoint = endpoint == null || endpoint.isBlank() ? "" : GovernancePlatformValidators.label(endpoint, "endpoint");
        this.authMode = authMode == null ? GovernanceConnectorAuthMode.NONE : authMode;
        this.accessMode = accessMode == null ? GovernanceConnectorAccessMode.READ_ONLY : accessMode;
        this.state = state == null ? GovernanceConnectorState.DISABLED : state;
        this.testEnabled = testEnabled;
        this.capabilities = capabilities == null || capabilities.isEmpty()
                ? List.of(GovernanceConnectorCapability.WRITE_DISABLED)
                : List.copyOf(capabilities);
        this.lastMessage = lastMessage == null || lastMessage.isBlank()
                ? "Configured locally; production writes disabled"
                : GovernancePlatformValidators.label(lastMessage, "lastMessage");
        this.attributes = Collections.unmodifiableMap(GovernancePlatformValidators.attributes(attributes));
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.updatedAt = updatedAt == null ? this.createdAt : updatedAt;
    }

    /** Returns the stable connector key. */
    public String connectorKey() { return connectorKey; }
    /** Returns the connector kind. */
    public GovernanceConnectorKind kind() { return kind; }
    /** Returns the display name. */
    public String displayName() { return displayName; }
    /** Returns the sanitized endpoint. */
    public String endpoint() { return endpoint; }
    /** Returns the configured authentication mode. */
    public GovernanceConnectorAuthMode authMode() { return authMode; }
    /** Returns the local access mode. */
    public GovernanceConnectorAccessMode accessMode() { return accessMode; }
    /** Returns the latest local connector state. */
    public GovernanceConnectorState state() { return state; }
    /** Returns whether explicit test actions are enabled. */
    public boolean testEnabled() { return testEnabled; }
    /** Returns the declared capabilities. */
    public List<GovernanceConnectorCapability> capabilities() { return capabilities; }
    /** Returns the latest low-cardinality message. */
    public String lastMessage() { return lastMessage; }
    /** Returns public low-cardinality attributes. */
    public Map<String, String> attributes() { return attributes; }
    /** Returns creation time. */
    public Instant createdAt() { return createdAt; }
    /** Returns update time. */
    public Instant updatedAt() { return updatedAt; }
}
