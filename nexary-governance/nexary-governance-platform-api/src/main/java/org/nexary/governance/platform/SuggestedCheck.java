package org.nexary.governance.platform;

/** Suggested read-only next check for an incident candidate. */
public final class SuggestedCheck {
    private final String resourceKey;
    private final String message;

    /** Creates a suggested check. */
    public SuggestedCheck(String resourceKey, String message) {
        this.resourceKey = GovernancePlatformValidators.token(resourceKey, "resourceKey");
        this.message = GovernancePlatformValidators.label(message, "message");
    }

    /** Returns the resource key to inspect first. */
    public String resourceKey() { return resourceKey; }
    /** Returns the short suggested check message. */
    public String message() { return message; }
}
