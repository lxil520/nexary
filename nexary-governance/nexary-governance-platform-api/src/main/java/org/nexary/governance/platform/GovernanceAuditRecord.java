package org.nexary.governance.platform;

import java.time.Instant;

/** Local audit record for platform dry-run, export, and notification actions. */
public final class GovernanceAuditRecord {
    private final String auditKey;
    private final GovernanceAuditAction action;
    private final String subjectKey;
    private final String result;
    private final String message;
    private final Instant createdAt;

    /** Creates a local audit record. */
    public GovernanceAuditRecord(
            String auditKey,
            GovernanceAuditAction action,
            String subjectKey,
            String result,
            String message,
            Instant createdAt) {
        this.auditKey = GovernancePlatformValidators.token(auditKey, "auditKey");
        this.action = action == null ? GovernanceAuditAction.PLAN_GENERATED : action;
        this.subjectKey = GovernancePlatformValidators.token(subjectKey, "subjectKey");
        this.result = GovernancePlatformValidators.token(result == null ? "OK" : result, "result");
        this.message = GovernancePlatformValidators.label(message == null ? "Local platform action recorded" : message, "message");
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    /** Returns the audit key. */
    public String auditKey() { return auditKey; }
    /** Returns the recorded action. */
    public GovernanceAuditAction action() { return action; }
    /** Returns the audited subject key. */
    public String subjectKey() { return subjectKey; }
    /** Returns the result bucket. */
    public String result() { return result; }
    /** Returns the low-cardinality audit message. */
    public String message() { return message; }
    /** Returns creation time. */
    public Instant createdAt() { return createdAt; }
}
