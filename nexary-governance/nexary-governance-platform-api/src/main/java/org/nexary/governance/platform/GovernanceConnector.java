package org.nexary.governance.platform;

import java.util.Map;
import java.util.Objects;

/** Registered read-only connector such as Nexary SDK, Prometheus, Sentinel, or IM dry-run. */
public final class GovernanceConnector {
    private final String connectorKey;
    private final GovernanceConnectorKind kind;
    private final GovernanceConnectorState state;
    private final String displayName;
    private final String lastMessage;
    private final Map<String, String> attributes;

    /** Creates a connector descriptor. */
    public GovernanceConnector(
            String connectorKey,
            GovernanceConnectorKind kind,
            GovernanceConnectorState state,
            String displayName,
            String lastMessage,
            Map<String, String> attributes) {
        this.connectorKey = GovernancePlatformValidators.token(connectorKey, "connectorKey");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.state = state == null ? GovernanceConnectorState.DISABLED : state;
        this.displayName = GovernancePlatformValidators.label(displayName, "displayName");
        this.lastMessage = lastMessage == null ? "" : GovernancePlatformValidators.label(lastMessage, "lastMessage");
        this.attributes = GovernancePlatformValidators.attributes(attributes);
    }

    /** Returns the stable connector key. */
    public String connectorKey() {
        return connectorKey;
    }

    /** Returns the connector kind. */
    public GovernanceConnectorKind kind() {
        return kind;
    }

    /** Returns the connector health state. */
    public GovernanceConnectorState state() {
        return state;
    }

    /** Returns the display name. */
    public String displayName() {
        return displayName;
    }

    /** Returns a low-cardinality connector status message. */
    public String lastMessage() {
        return lastMessage;
    }

    /** Returns bounded low-cardinality attributes. */
    public Map<String, String> attributes() {
        return attributes;
    }
}
