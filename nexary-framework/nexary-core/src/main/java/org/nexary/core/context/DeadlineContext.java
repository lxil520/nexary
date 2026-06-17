package org.nexary.core.context;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;

/** Carries a request deadline through synchronous execution paths. */
public final class DeadlineContext {
    private static final ThreadLocal<Instant> CURRENT_DEADLINE = new ThreadLocal<>();

    private DeadlineContext() {
    }

    /** Sets the current request deadline. */
    public static void set(Instant deadline) {
        CURRENT_DEADLINE.set(deadline);
    }

    /** Returns the current request deadline when one is available. */
    public static Optional<Instant> current() {
        return Optional.ofNullable(CURRENT_DEADLINE.get());
    }

    /** Returns the remaining time before the deadline. */
    public static Optional<Duration> remaining() {
        return current().map(deadline -> Duration.between(Instant.now(), deadline));
    }

    /** Returns true when the current deadline exists and has passed. */
    public static boolean expired() {
        return current().map(deadline -> !Instant.now().isBefore(deadline)).orElse(false);
    }

    /** Clears the deadline bound to the current thread. */
    public static void clear() {
        CURRENT_DEADLINE.remove();
    }

    /** Runs an action with a scoped deadline and restores the previous value afterwards. */
    public static <T> T callWithDeadline(Instant deadline, Callable<T> action) throws Exception {
        Instant previous = CURRENT_DEADLINE.get();
        CURRENT_DEADLINE.set(deadline);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT_DEADLINE.remove();
            } else {
                CURRENT_DEADLINE.set(previous);
            }
        }
    }
}
