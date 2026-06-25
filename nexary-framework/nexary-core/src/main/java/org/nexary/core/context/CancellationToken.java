package org.nexary.core.context;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cooperative cancellation handle for one request chain.
 *
 * <p>The token id is used only for local registry lookup and downstream cancel
 * notification. It must not be copied into metrics, events, or console tables.</p>
 */
public final class CancellationToken {
    private final String cancellationId;
    private final AtomicReference<CancellationReason> reason = new AtomicReference<>(CancellationReason.NONE);
    private final AtomicReference<Instant> cancelledAt = new AtomicReference<>();

    private CancellationToken(String cancellationId) {
        this.cancellationId = hasText(cancellationId) ? cancellationId.trim() : UUID.randomUUID().toString();
    }

    /** Creates a token with a generated local cancellation id. */
    public static CancellationToken create() {
        return new CancellationToken(null);
    }

    /** Creates a token with the supplied cancellation id. */
    public static CancellationToken create(String cancellationId) {
        return new CancellationToken(cancellationId);
    }

    /** Returns the internal cancellation id used for propagation and lookup. */
    public String cancellationId() {
        return cancellationId;
    }

    /** Returns true when this token has been cancelled. */
    public boolean isCancelled() {
        return reason.get() != CancellationReason.NONE;
    }

    /** Returns the low-cardinality cancellation reason. */
    public CancellationReason reason() {
        return reason.get();
    }

    /** Returns when this token was cancelled, if it has been cancelled. */
    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt.get());
    }

    /** Cancels this token once and returns whether this call changed the state. */
    public boolean cancel(CancellationReason cancellationReason) {
        CancellationReason safeReason = cancellationReason == null || cancellationReason == CancellationReason.NONE
                ? CancellationReason.MANUAL
                : cancellationReason;
        boolean changed = reason.compareAndSet(CancellationReason.NONE, safeReason);
        if (changed) {
            cancelledAt.compareAndSet(null, Instant.now());
        }
        return changed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CancellationToken)) {
            return false;
        }
        CancellationToken that = (CancellationToken) other;
        return cancellationId.equals(that.cancellationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cancellationId);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
