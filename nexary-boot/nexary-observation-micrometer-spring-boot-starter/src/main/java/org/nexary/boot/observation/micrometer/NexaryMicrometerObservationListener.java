package org.nexary.boot.observation.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.observation.NexaryObservationListener;

/** Bridges provider-neutral Nexary observation events to Micrometer meters. */
public class NexaryMicrometerObservationListener implements NexaryObservationListener {
    private static final Set<String> TAG_WHITELIST = Set.of(
            "category",
            "operation",
            "provider",
            "outcome",
            "tier",
            "status",
            "failure_category",
            "boundary",
            "trigger",
            "skip_reason",
            "shard_presence",
            "store",
            "retry_attempt_bucket",
            "terminal_status",
            "retry_phase");
    private static final Set<String> FORBIDDEN_TAGS = Set.of(
            "cache_key",
            "key",
            "namespace",
            "raw_namespace",
            "message_id",
            "topic",
            "raw_topic",
            "consumer_group",
            "raw_consumer_group",
            "execution_id",
            "payload",
            "lock_token",
            "fencing_token",
            "owner_token",
            "exception",
            "exception_message",
            "stack_trace");

    private final MeterRegistry meterRegistry;
    private final String counterName;
    private final String timerName;

    public NexaryMicrometerObservationListener(
            MeterRegistry meterRegistry,
            NexaryObservationMicrometerProperties properties) {
        this.meterRegistry = meterRegistry;
        this.counterName = metricName(properties.getCounterName(), "nexary.observation.events.total");
        this.timerName = metricName(properties.getTimerName(), "nexary.observation.events.duration");
    }

    @Override
    public void onObservation(NexaryObservationEvent event) {
        if (event == null) {
            return;
        }
        Iterable<Tag> tags = tags(event);
        meterRegistry.counter(counterName, tags).increment();
        meterRegistry.timer(timerName, tags).record(event.duration().toNanos(), TimeUnit.NANOSECONDS);
    }

    private Iterable<Tag> tags(NexaryObservationEvent event) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("category", sanitize(event.category().name())));
        tags.add(Tag.of("operation", sanitize(event.operation())));
        event.tags().forEach((key, value) -> {
            String normalizedKey = normalizeKey(key);
            if ("failure".equals(normalizedKey)) {
                normalizedKey = "failure_category";
            }
            if (normalizedKey != null && TAG_WHITELIST.contains(normalizedKey) && !FORBIDDEN_TAGS.contains(normalizedKey)) {
                if (!"category".equals(normalizedKey) && !"operation".equals(normalizedKey)) {
                    tags.add(Tag.of(normalizedKey, sanitize(value)));
                }
            }
        });
        return tags;
    }

    private static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private static String metricName(String configured, String fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        return configured.trim();
    }
}
