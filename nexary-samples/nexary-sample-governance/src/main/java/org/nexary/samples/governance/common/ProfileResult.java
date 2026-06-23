package org.nexary.samples.governance.common;

/** Profile response returned by the governance sample. */
public final class ProfileResult {
    private final String userId;
    private final String displayName;
    private final String source;

    public ProfileResult(String userId, String displayName, String source) {
        this.userId = userId;
        this.displayName = displayName;
        this.source = source;
    }

    public String userId() {
        return userId;
    }

    public String getUserId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String source() {
        return source;
    }

    public String getSource() {
        return source;
    }
}
