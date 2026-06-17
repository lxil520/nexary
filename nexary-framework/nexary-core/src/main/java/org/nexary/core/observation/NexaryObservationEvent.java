package org.nexary.core.observation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.fault.FaultSignal;

/** Standard observation event emitted by cache, messaging, job, and SPI components. */
public record NexaryObservationEvent(
        EventCategory category,
        String operation,
        Instant startedAt,
        Instant endedAt,
        TrafficTag trafficTag,
        FaultSignal faultSignal,
        Map<String, String> tags) {
    public NexaryObservationEvent(
            EventCategory category,
            String operation,
            Instant startedAt,
            Instant endedAt,
            TrafficTag trafficTag,
            FaultSignal faultSignal) {
        this(category, operation, startedAt, endedAt, trafficTag, faultSignal, Map.of());
    }

    public NexaryObservationEvent {
        category = category == null ? EventCategory.CORE : category;
        operation = operation == null ? "unknown" : operation;
        startedAt = startedAt == null ? Instant.now() : startedAt;
        endedAt = endedAt == null ? Instant.now() : endedAt;
        trafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
        tags = tags == null ? Map.of() : Map.copyOf(tags);
    }

    /** Duration of the observed operation. */
    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }

    /** Event category. */
    public enum EventCategory { CORE, CACHE, MESSAGING, JOB, SPI }
}
