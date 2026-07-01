package org.nexary.governance.platform;

import java.util.Map;
import java.util.Objects;

/** Local notification route metadata managed by the governance platform. */
public final class GovernanceNotificationRoute {
    private final String routeKey;
    private final String channel;
    private final String displayName;
    private final String targetTeam;
    private final GovernanceSignalSeverity minSeverity;
    private final GovernanceNotificationMode mode;
    private final GovernanceConnectorState state;
    private final boolean testEnabled;
    private final String lastMessage;
    private final Map<String, String> attributes;

    /** Creates local notification route metadata. */
    public GovernanceNotificationRoute(
            String routeKey,
            String channel,
            String displayName,
            String targetTeam,
            GovernanceSignalSeverity minSeverity,
            GovernanceNotificationMode mode,
            GovernanceConnectorState state,
            boolean testEnabled,
            String lastMessage,
            Map<String, String> attributes) {
        this.routeKey = GovernancePlatformValidators.token(routeKey, "routeKey");
        this.channel = GovernancePlatformValidators.token(channel, "channel");
        this.displayName = GovernancePlatformValidators.label(displayName, "displayName");
        this.targetTeam = GovernancePlatformValidators.token(Objects.requireNonNullElse(targetTeam, "platform-team"), "targetTeam");
        this.minSeverity = minSeverity == null ? GovernanceSignalSeverity.CRITICAL : minSeverity;
        this.mode = mode == null ? GovernanceNotificationMode.DRY_RUN : mode;
        this.state = state == null ? GovernanceConnectorState.DISABLED : state;
        this.testEnabled = testEnabled;
        this.lastMessage = lastMessage == null || lastMessage.isBlank()
                ? "none"
                : GovernancePlatformValidators.label(lastMessage, "lastMessage");
        this.attributes = GovernancePlatformValidators.attributes(attributes);
    }

    /** Returns the stable route key. */
    public String routeKey() { return routeKey; }
    /** Returns the channel key. */
    public String channel() { return channel; }
    /** Returns the display name. */
    public String displayName() { return displayName; }
    /** Returns the target team key. */
    public String targetTeam() { return targetTeam; }
    /** Returns the minimum severity for previews. */
    public GovernanceSignalSeverity minSeverity() { return minSeverity; }
    /** Returns the route mode. */
    public GovernanceNotificationMode mode() { return mode; }
    /** Returns connector state. */
    public GovernanceConnectorState state() { return state; }
    /** Returns whether explicit test delivery is enabled. */
    public boolean testEnabled() { return testEnabled; }
    /** Returns the last low-cardinality status message. */
    public String lastMessage() { return lastMessage; }
    /** Returns bounded public attributes. */
    public Map<String, String> attributes() { return attributes; }
}
