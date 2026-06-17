package org.nexary.messaging;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;

/** Internal helpers for provider-neutral messaging observation events. */
public final class MessageObservationSupport {
    public static final String CAPABILITY = "messaging";

    private MessageObservationSupport() {
    }

    /** Publishes a messaging observation event with only the standard bounded tags. */
    public static void publish(
            NexaryObservationPublisher publisher,
            String operation,
            String provider,
            String outcome) {
        publish(publisher, operation, provider, outcome, Collections.emptyMap(), null);
    }

    /** Publishes a messaging observation event while filtering tags to the supported bounded tag set. */
    public static void publish(
            NexaryObservationPublisher publisher,
            String operation,
            String provider,
            String outcome,
            Map<String, String> tags,
            Throwable error) {
        NexaryObservationPublisher safePublisher = publisher == null ? NexaryObservationPublisher.noop() : publisher;
        Instant now = Instant.now();
        safePublisher.publish(new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.MESSAGING,
                sanitize(operation, "unknown"),
                now,
                now,
                null,
                null,
                boundedTags(provider, outcome, tags, error)));
    }

    /** Creates bounded retry attempt tags. */
    public static Map<String, String> retryTags(int attempt) {
        return Collections.singletonMap("retry_attempt_bucket", attemptBucket(attempt));
    }

    /** Creates bounded terminal status tags. */
    public static Map<String, String> terminalTags(String terminalStatus) {
        return Collections.singletonMap("terminal_status", sanitize(terminalStatus, "unknown"));
    }

    /** Creates bounded provider boundary tags. */
    public static Map<String, String> boundaryTags(String boundary) {
        return Collections.singletonMap("boundary", sanitize(boundary, "unknown"));
    }

    /** Maps a publish result to a bounded outcome value. */
    public static String outcome(MessagePublishResult result) {
        if (result == null || result.status() == null) {
            return "unknown";
        }
        return result.status() == MessagePublishResult.PublishStatus.SUCCESS ? "success" : "failure";
    }

    /** Maps a consume result to a bounded outcome value. */
    public static String outcome(MessageConsumeResult result) {
        if (result == null || result.status() == null) {
            return "unknown";
        }
        switch (result.status()) {
            case SUCCESS:
                return "success";
            case DUPLICATE:
                return "duplicate";
            case RETRY:
                return "retry";
            case DEAD_LETTER:
                return "dead_letter";
            case FAILED:
                return "failure";
            default:
                return "unknown";
        }
    }

    /** Buckets retry attempts into bounded tag values. */
    public static String attemptBucket(int attempt) {
        if (attempt <= 0) {
            return "none";
        }
        if (attempt == 1) {
            return "1";
        }
        if (attempt <= 3) {
            return "2_3";
        }
        return "4_plus";
    }

    /** Maps an exception to a bounded failure category without exposing exception details. */
    public static String failureCategory(Throwable error) {
        if (error == null) {
            return "none";
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return "application";
        }
        if (error instanceof java.util.concurrent.TimeoutException) {
            return "timeout";
        }
        return "system";
    }

    private static Map<String, String> boundedTags(
            String provider,
            String outcome,
            Map<String, String> extraTags,
            Throwable error) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("capability", CAPABILITY);
        tags.put("provider", sanitize(provider, "unknown"));
        tags.put("outcome", sanitize(outcome, "unknown"));
        tags.put("failure_category", failureCategory(error));
        if (extraTags != null) {
            extraTags.forEach((key, value) -> {
                String safeKey = sanitizeKey(key);
                if (safeKey != null && isAllowedTag(safeKey)) {
                    tags.put(safeKey, sanitize(value, "unknown"));
                }
            });
        }
        return tags;
    }

    private static boolean isAllowedTag(String key) {
        return "capability".equals(key)
                || "operation".equals(key)
                || "provider".equals(key)
                || "outcome".equals(key)
                || "retry_attempt_bucket".equals(key)
                || "terminal_status".equals(key)
                || "failure_category".equals(key)
                || "boundary".equals(key);
    }

    private static String sanitizeKey(String value) {
        if (isBlank(value)) {
            return null;
        }
        return value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static String sanitize(String value, String fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
