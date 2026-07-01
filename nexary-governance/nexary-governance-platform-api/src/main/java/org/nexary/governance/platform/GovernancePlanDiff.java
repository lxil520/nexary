package org.nexary.governance.platform;

import java.util.Objects;

/** Suggested before/after diff for human review. */
public final class GovernancePlanDiff {
    private final String fieldKey;
    private final String beforeValue;
    private final String afterValue;
    private final String reason;

    /** Creates a suggested diff. */
    public GovernancePlanDiff(String fieldKey, String beforeValue, String afterValue, String reason) {
        this.fieldKey = GovernancePlatformValidators.token(fieldKey, "fieldKey");
        this.beforeValue = GovernancePlatformValidators.label(Objects.requireNonNullElse(beforeValue, "unknown"), "beforeValue");
        this.afterValue = GovernancePlatformValidators.label(Objects.requireNonNullElse(afterValue, "review-required"), "afterValue");
        this.reason = GovernancePlatformValidators.label(Objects.requireNonNullElse(reason, "Evidence requires review"), "reason");
    }

    /** Returns the changed field key. */
    public String fieldKey() {
        return fieldKey;
    }

    /** Returns the current or observed value. */
    public String beforeValue() {
        return beforeValue;
    }

    /** Returns the suggested value. */
    public String afterValue() {
        return afterValue;
    }

    /** Returns the low-cardinality reason. */
    public String reason() {
        return reason;
    }
}
