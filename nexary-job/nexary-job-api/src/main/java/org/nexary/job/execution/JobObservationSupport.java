package org.nexary.job.execution;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;

/** Internal helpers for provider-neutral job observation events. */
public final class JobObservationSupport {
    public static final String CAPABILITY = "job";
    public static final String OPERATION_TRIGGER = "job.trigger";
    public static final String OPERATION_EXECUTION_START = "job.execution.start";
    public static final String OPERATION_EXECUTION_END = "job.execution.end";
    public static final String OPERATION_RETRY_ATTEMPT = "job.retry.attempt";
    public static final String OPERATION_TIMEOUT = "job.execution.timeout";
    public static final String OPERATION_SKIP = "job.execution.skip";
    public static final String OPERATION_LISTENER_NOTIFICATION = "job.listener.notification";
    public static final String OPERATION_STORE_SAVE = "job.store.save";
    public static final String OPERATION_STORE_FIND = "job.store.find";
    public static final String OPERATION_STORE_RETENTION_EXPIRY = "job.store.retention_expiry";
    public static final String OPERATION_SCHEDULER_RUN = "job.scheduler.run";
    public static final String OPERATION_XXLJOB_BRIDGE_TRIGGER = "job.xxljob.bridge.trigger";

    private JobObservationSupport() {
    }

    /** Publishes a bounded job event. */
    public static void publish(
            NexaryObservationPublisher publisher,
            String operation,
            String provider,
            JobExecutionTrigger trigger,
            String status,
            Map<String, String> tags,
            Throwable error) {
        Instant now = Instant.now();
        publish(publisher, operation, provider, trigger, status, tags, error, now, now);
    }

    /** Publishes a bounded job event with explicit timing. */
    public static void publish(
            NexaryObservationPublisher publisher,
            String operation,
            String provider,
            JobExecutionTrigger trigger,
            String status,
            Map<String, String> tags,
            Throwable error,
            Instant startedAt,
            Instant endedAt) {
        NexaryObservationPublisher safePublisher = publisher == null ? NexaryObservationPublisher.noop() : publisher;
        safePublisher.publish(new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.JOB,
                sanitize(operation, "unknown"),
                startedAt,
                endedAt,
                null,
                null,
                boundedTags(operation, provider, trigger, status, tags, error)));
    }

    /** Creates a tag map for shard metadata without exposing shard indexes. */
    public static Map<String, String> shardTags(JobExecutionRequest request) {
        if (request == null || request.context() == null) {
            return Map.of("shard_presence", "false");
        }
        return Map.of("shard_presence", request.context().shardTotal() > 1 ? "true" : "false");
    }

    /** Creates a bounded retry attempt tag. */
    public static Map<String, String> retryTags(int attempt, int maxAttempts) {
        return Map.of(
                "retry_attempt_bucket", attemptBucket(attempt),
                "retry_phase", attempt >= maxAttempts ? "final" : attempt == 1 ? "first" : "retry");
    }

    /** Creates a bounded skip reason tag. */
    public static Map<String, String> skipTags(String reason) {
        return Map.of("skip_reason", skipReason(reason));
    }

    /** Maps raw skip text to a bounded reason. */
    public static String skipReason(String reason) {
        String value = sanitize(reason, "unknown");
        if (value.contains("misfired")) {
            return "misfire";
        }
        if (value.contains("already_running")) {
            return "concurrency";
        }
        if (value.contains("single_instance")) {
            return "single_instance";
        }
        if (value.contains("shard_assigned")) {
            return "shard_assignment";
        }
        return "unknown";
    }

    /** Maps execution status into a bounded tag value. */
    public static String status(JobExecutionStatus status) {
        return status == null ? "unknown" : sanitize(status.name(), "unknown");
    }

    /** Maps trigger into a bounded tag value. */
    public static String trigger(JobExecutionTrigger trigger) {
        return trigger == null ? "unknown" : sanitize(trigger.name(), "unknown");
    }

    /** Maps failure to a bounded category without carrying error messages. */
    public static String failureCategory(Throwable error) {
        if (error == null) {
            return "none";
        }
        if (error instanceof TimeoutException) {
            return "timeout";
        }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return "application";
        }
        return "system";
    }

    private static Map<String, String> boundedTags(
            String operation,
            String provider,
            JobExecutionTrigger trigger,
            String status,
            Map<String, String> extraTags,
            Throwable error) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("capability", CAPABILITY);
        tags.put("operation", sanitize(operation, "unknown"));
        tags.put("provider", sanitize(provider, "unknown"));
        tags.put("trigger", trigger(trigger));
        tags.put("status", sanitize(status, "unknown"));
        tags.put("skip_reason", "none");
        tags.put("shard_presence", "unknown");
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
        return switch (key) {
            case "capability",
                    "operation",
                    "provider",
                    "trigger",
                    "status",
                    "skip_reason",
                    "shard_presence",
                    "failure_category",
                    "retry_attempt_bucket",
                    "retry_phase",
                    "store" -> true;
            default -> false;
        };
    }

    private static String attemptBucket(int attempt) {
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

    private static String sanitizeKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }
}
