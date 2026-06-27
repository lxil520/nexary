package org.nexary.governance.platform;

import java.time.Instant;

/** Read-only connector status returned by platform queries. */
public final class GovernanceConnectorStatus {
    private final String connectorKey;
    private final GovernanceConnectorKind kind;
    private final GovernanceConnectorState state;
    private final String displayName;
    private final String lastMessage;
    private final Instant lastSeenAt;

    /** Creates a connector status. */
    public GovernanceConnectorStatus(
            String connectorKey,
            GovernanceConnectorKind kind,
            GovernanceConnectorState state,
            String displayName,
            String lastMessage,
            Instant lastSeenAt) {
        this.connectorKey = GovernancePlatformValidators.token(connectorKey, "connectorKey");
        this.kind = kind == null ? GovernanceConnectorKind.NEXARY_SDK : kind;
        this.state = state == null ? GovernanceConnectorState.DISABLED : state;
        this.displayName = GovernancePlatformValidators.label(displayName, "displayName");
        this.lastMessage = lastMessage == null ? "" : GovernancePlatformValidators.label(lastMessage, "lastMessage");
        this.lastSeenAt = lastSeenAt;
    }

    /** Returns the connector key. */
    public String connectorKey() { return connectorKey; }
    /** Returns the connector kind. */
    public GovernanceConnectorKind kind() { return kind; }
    /** Returns the connector state. */
    public GovernanceConnectorState state() { return state; }
    /** Returns the display name. */
    public String displayName() { return displayName; }
    /** Returns the latest bounded status message. */
    public String lastMessage() { return lastMessage; }
    /** Returns when this connector was last seen. */
    public Instant lastSeenAt() { return lastSeenAt; }
}
