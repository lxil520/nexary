package org.nexary.core.governance;

import java.time.Instant;
import java.util.Objects;

/** Result of checking whether an operation may start before its deadline. */
public final class TimeoutDecision {
    private static final TimeoutDecision ALLOWED = new TimeoutDecision(true, "allowed");
    private final boolean allowed;
    private final String reason;

    /** Creates a timeout decision with a bounded reason. */
    public TimeoutDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = reason == null || reason.trim().isEmpty() ? "unknown" : reason.trim();
    }

    /** Returns an allowed decision. */
    public static TimeoutDecision allowed() {
        return ALLOWED;
    }

    /** Returns a rejected decision for an expired deadline. */
    public static TimeoutDecision deadlineExceeded() {
        return new TimeoutDecision(false, "deadline_exceeded");
    }

    /** Evaluates the supplied context against the supplied clock instant. */
    public static TimeoutDecision from(GovernanceContext context, Instant now) {
        if (context == null || !context.deadline().isPresent()) {
            return allowed();
        }
        Instant current = now == null ? Instant.now() : now;
        return current.isBefore(context.deadline().get()) ? allowed() : deadlineExceeded();
    }

    /** Returns true when the operation may continue. */
    public boolean isAllowed() {
        return allowed;
    }

    /** Returns a bounded reason suitable for policy logs or event tags. */
    public String reason() {
        return reason;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TimeoutDecision)) {
            return false;
        }
        TimeoutDecision that = (TimeoutDecision) other;
        return allowed == that.allowed && reason.equals(that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowed, reason);
    }

    @Override
    public String toString() {
        return "TimeoutDecision[allowed=" + allowed + ", reason=" + reason + ']';
    }
}
