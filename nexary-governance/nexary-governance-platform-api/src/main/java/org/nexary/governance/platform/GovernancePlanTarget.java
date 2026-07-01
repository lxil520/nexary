package org.nexary.governance.platform;

import java.util.Objects;

/** Target of a governance review plan. */
public final class GovernancePlanTarget {
    private final GovernancePlanTargetKind kind;
    private final String targetKey;
    private final String displayName;

    /** Creates a plan target. */
    public GovernancePlanTarget(GovernancePlanTargetKind kind, String targetKey, String displayName) {
        this.kind = kind == null ? GovernancePlanTargetKind.OWNERSHIP_MAPPING : kind;
        this.targetKey = GovernancePlatformValidators.token(targetKey, "targetKey");
        this.displayName = GovernancePlatformValidators.label(
                Objects.requireNonNullElse(displayName, targetKey), "displayName");
    }

    /** Returns the target kind. */
    public GovernancePlanTargetKind kind() {
        return kind;
    }

    /** Returns the stable target key. */
    public String targetKey() {
        return targetKey;
    }

    /** Returns the display name. */
    public String displayName() {
        return displayName;
    }
}
