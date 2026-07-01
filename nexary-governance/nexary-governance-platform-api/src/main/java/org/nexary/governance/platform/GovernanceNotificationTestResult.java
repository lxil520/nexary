package org.nexary.governance.platform;

import java.time.Instant;

/** Result of an explicitly marked test notification attempt. */
public final class GovernanceNotificationTestResult {
    private final String testKey;
    private final String routeKey;
    private final boolean accepted;
    private final String status;
    private final String message;
    private final Instant attemptedAt;
    private final GovernanceNotificationPreview preview;

    /** Creates a notification test result. */
    public GovernanceNotificationTestResult(
            String testKey,
            String routeKey,
            boolean accepted,
            String status,
            String message,
            Instant attemptedAt,
            GovernanceNotificationPreview preview) {
        this.testKey = GovernancePlatformValidators.token(testKey, "testKey");
        this.routeKey = GovernancePlatformValidators.token(routeKey, "routeKey");
        this.accepted = accepted;
        this.status = GovernancePlatformValidators.token(status == null ? "DISABLED" : status, "status");
        this.message = GovernancePlatformValidators.label(message == null ? "Notification test was not sent" : message, "message");
        this.attemptedAt = attemptedAt == null ? Instant.now() : attemptedAt;
        this.preview = preview;
    }

    /** Returns the stable local test key. */
    public String testKey() { return testKey; }
    /** Returns the route key. */
    public String routeKey() { return routeKey; }
    /** Returns whether the test was accepted for delivery. */
    public boolean accepted() { return accepted; }
    /** Returns the test status. */
    public String status() { return status; }
    /** Returns the low-cardinality result message. */
    public String message() { return message; }
    /** Returns the attempt time. */
    public Instant attemptedAt() { return attemptedAt; }
    /** Returns the exact TEST preview used by the attempt. */
    public GovernanceNotificationPreview preview() { return preview; }
}
