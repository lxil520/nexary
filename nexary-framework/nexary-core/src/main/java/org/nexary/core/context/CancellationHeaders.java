package org.nexary.core.context;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** HTTP header names and parsers used for request deadline and cancellation propagation. */
public final class CancellationHeaders {
    /** Absolute deadline as epoch milliseconds. */
    public static final String DEADLINE_EPOCH_MILLIS = "Nexary-Deadline-Epoch-Millis";

    /** Relative timeout from the receiving hop, in milliseconds. */
    public static final String TIMEOUT_MILLIS = "Nexary-Timeout-Millis";

    /** Internal cancellation id used only for propagation and local lookup. */
    public static final String CANCELLATION_ID = "Nexary-Cancellation-Id";

    /** Low-cardinality cancellation reason. */
    public static final String CANCEL_REASON = "Nexary-Cancel-Reason";

    private CancellationHeaders() {
    }

    /** Returns a cancellation id from case-insensitive headers, if present. */
    public static Optional<String> cancellationId(Map<String, String> headers) {
        return header(headers, CANCELLATION_ID).filter(CancellationHeaders::hasText);
    }

    /** Returns a low-cardinality cancellation reason from headers, or {@link CancellationReason#NONE}. */
    public static CancellationReason cancellationReason(Map<String, String> headers) {
        return header(headers, CANCEL_REASON)
                .map(value -> value.trim().toUpperCase(Locale.ROOT).replace('-', '_'))
                .map(value -> {
                    try {
                        return CancellationReason.valueOf(value);
                    } catch (IllegalArgumentException ex) {
                        return CancellationReason.NONE;
                    }
                })
                .orElse(CancellationReason.NONE);
    }

    /** Resolves the earlier deadline from absolute and relative timeout headers. */
    public static Optional<Instant> deadline(Map<String, String> headers, Instant receivedAt) {
        Instant now = receivedAt == null ? Instant.now() : receivedAt;
        Optional<Instant> absolute = header(headers, DEADLINE_EPOCH_MILLIS).flatMap(CancellationHeaders::epochMillis);
        Optional<Instant> relative = header(headers, TIMEOUT_MILLIS)
                .flatMap(CancellationHeaders::millis)
                .map(timeout -> now.plus(timeout));
        if (absolute.isPresent() && relative.isPresent()) {
            return Optional.of(absolute.get().isBefore(relative.get()) ? absolute.get() : relative.get());
        }
        return absolute.isPresent() ? absolute : relative;
    }

    private static Optional<String> header(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return Optional.ofNullable(entry.getValue());
            }
        }
        return Optional.empty();
    }

    private static Optional<Instant> epochMillis(String value) {
        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value.trim())));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> millis(String value) {
        try {
            long millis = Long.parseLong(value.trim());
            return millis > 0 ? Optional.of(Duration.ofMillis(millis)) : Optional.empty();
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
