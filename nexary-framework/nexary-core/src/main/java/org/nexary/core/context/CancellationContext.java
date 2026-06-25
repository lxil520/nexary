package org.nexary.core.context;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-bound cooperative cancellation context plus a local token registry. */
public final class CancellationContext {
    private static final ThreadLocal<CancellationToken> CURRENT_TOKEN = new ThreadLocal<>();
    private static final ConcurrentMap<String, CancellationToken> TOKENS = new ConcurrentHashMap<>();

    private CancellationContext() {
    }

    /** Returns the cancellation token bound to the current thread, if available. */
    public static Optional<CancellationToken> current() {
        return Optional.ofNullable(CURRENT_TOKEN.get());
    }

    /** Returns true when the current thread-bound token has been cancelled. */
    public static boolean cancelled() {
        return current().map(CancellationToken::isCancelled).orElse(false);
    }

    /** Returns the current low-cardinality cancellation reason, or {@link CancellationReason#NONE}. */
    public static CancellationReason reason() {
        return current().map(CancellationToken::reason).orElse(CancellationReason.NONE);
    }

    /** Binds a token to the current thread and registers it for downstream cancellation lookup. */
    public static void set(CancellationToken token) {
        if (token == null) {
            clear();
            return;
        }
        CURRENT_TOKEN.set(token);
        TOKENS.put(token.cancellationId(), token);
    }

    /** Clears the current thread-bound token without cancelling it. */
    public static void clear() {
        CURRENT_TOKEN.remove();
    }

    /** Cancels a registered token by id. */
    public static boolean cancel(String cancellationId, CancellationReason reason) {
        if (!hasText(cancellationId)) {
            return false;
        }
        CancellationToken token = TOKENS.get(cancellationId.trim());
        return token != null && token.cancel(reason);
    }

    /** Returns a registered token by id, if it is still active in this JVM. */
    public static Optional<CancellationToken> token(String cancellationId) {
        if (!hasText(cancellationId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(TOKENS.get(cancellationId.trim()));
    }

    /** Runs an action with a scoped cancellation token and restores the previous token afterwards. */
    public static <T> T callWithToken(CancellationToken token, Callable<T> action) throws Exception {
        if (action == null) {
            throw new NullPointerException("action");
        }
        CancellationToken previous = CURRENT_TOKEN.get();
        if (token == null) {
            CURRENT_TOKEN.remove();
        } else {
            set(token);
        }
        try {
            return action.call();
        } finally {
            if (token != null) {
                TOKENS.remove(token.cancellationId(), token);
            }
            if (previous == null) {
                CURRENT_TOKEN.remove();
            } else {
                set(previous);
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
