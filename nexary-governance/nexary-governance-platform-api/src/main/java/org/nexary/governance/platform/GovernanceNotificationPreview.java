package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/** Rendered dry-run notification preview. */
public final class GovernanceNotificationPreview {
    private final String routeKey;
    private final String incidentKey;
    private final String subject;
    private final String body;
    private final List<String> recipients;
    private final GovernanceNotificationMode mode;
    private final Instant createdAt;

    /** Creates a notification preview. */
    public GovernanceNotificationPreview(
            String routeKey,
            String incidentKey,
            String subject,
            String body,
            List<String> recipients,
            GovernanceNotificationMode mode,
            Instant createdAt) {
        this.routeKey = GovernancePlatformValidators.token(routeKey, "routeKey");
        this.incidentKey = GovernancePlatformValidators.token(incidentKey, "incidentKey");
        this.subject = GovernancePlatformValidators.label(subject, "subject");
        this.body = GovernancePlatformValidators.label(body, "body");
        this.recipients = recipients == null || recipients.isEmpty()
                ? Collections.emptyList()
                : recipients.stream().map(value -> GovernancePlatformValidators.token(value, "recipient")).toList();
        this.mode = mode == null ? GovernanceNotificationMode.DRY_RUN : mode;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** Returns the route key. */
    public String routeKey() { return routeKey; }
    /** Returns the bound incident key. */
    public String incidentKey() { return incidentKey; }
    /** Returns the preview subject. */
    public String subject() { return subject; }
    /** Returns the preview body. */
    public String body() { return body; }
    /** Returns recipient group keys. */
    public List<String> recipients() { return recipients; }
    /** Returns preview mode. */
    public GovernanceNotificationMode mode() { return mode; }
    /** Returns preview creation time. */
    public Instant createdAt() { return createdAt; }
}
