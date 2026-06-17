package org.nexary.cache.tiered;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationPublisher;

final class TieredCacheObservation {
    private static final String CAPABILITY = "cache";
    private static final String PROVIDER = "tiered";
    private static final String NONE = "none";

    private TieredCacheObservation() {
    }

    static void publish(
            NexaryObservationPublisher publisher,
            String operation,
            String tier,
            String outcome,
            Instant startedAt) {
        publish(publisher, operation, tier, outcome, NONE, startedAt);
    }

    static void publish(
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
        tags.put("tier", tier == null || tier.isBlank() ? NONE : tier);
        tags.put("outcome", outcome == null || outcome.isBlank() ? "unknown" : outcome);
        tags.put("failure", failure == null || failure.isBlank() ? NONE : failure);
        safePublisher.publish(new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.CACHE,
                operation,
                startedAt == null ? endedAt : startedAt,
                endedAt,
                null,
                null,
                tags));
    }

    static String failureCategory(RuntimeException ex) {
        if (ex instanceof IllegalArgumentException) {
            return "invalid_request";
        }
        if (ex instanceof IllegalStateException) {
            return "state";
        }
        return "runtime";
    }
}
