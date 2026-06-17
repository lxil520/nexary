package org.nexary.cache.redis.boot4;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;

/** Internal Redis cache observation helper that emits bounded provider-neutral tags. */
public final class RedisBoot4CacheObservation {
    private static final String CAPABILITY = "cache";
    private static final String PROVIDER = "redis";
    private static final String NONE = "none";

    private RedisBoot4CacheObservation() {
    }

    public static void publish(
            NexaryObservationPublisher publisher,
            String operation,
            String tier,
            String outcome,
            Instant startedAt) {
        publish(publisher, operation, tier, outcome, NONE, startedAt);
    }

    public static void publish(
            NexaryObservationPublisher publisher,
            String operation,
            String tier,
            String outcome,
            String failure,
            Instant startedAt) {
        NexaryObservationPublisher safePublisher = publisher == null ? NexaryObservationPublisher.noop() : publisher;
        Instant endedAt = Instant.now();
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("capability", CAPABILITY);
        tags.put("operation", operation);
        tags.put("provider", PROVIDER);
        tags.put("tier", hasNoText(tier) ? NONE : tier);
        tags.put("outcome", hasNoText(outcome) ? "unknown" : outcome);
        tags.put("failure", hasNoText(failure) ? NONE : failure);
        safePublisher.publish(new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.CACHE,
                operation,
                startedAt == null ? endedAt : startedAt,
                endedAt,
                null,
                null,
                tags));
    }

    public static String failureCategory(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException) {
            return "invalid_request";
        }
        if (ex instanceof IllegalStateException) {
            return "state";
        }
        return "runtime";
    }

    private static boolean hasNoText(String value) {
        return value == null || value.trim().isEmpty();
    }
}
