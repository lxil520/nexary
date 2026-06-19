package org.nexary.core.observation;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.fault.FaultSignal;

/** Standard observation event emitted by cache, messaging, job, and SPI components. */
public final class NexaryObservationEvent {
    private final EventCategory category;
    private final String operation;
    private final Instant startedAt;
    private final Instant endedAt;
    private final TrafficTag trafficTag;
    private final FaultSignal faultSignal;
    private final Map<String, String> tags;

    public NexaryObservationEvent(
            EventCategory category,
            String operation,
            Instant startedAt,
            Instant endedAt,
            TrafficTag trafficTag,
            FaultSignal faultSignal) {
        this(category, operation, startedAt, endedAt, trafficTag, faultSignal, Collections.emptyMap());
    }

    public NexaryObservationEvent(
            EventCategory category,
            String operation,
            Instant startedAt,
            Instant endedAt,
            TrafficTag trafficTag,
            FaultSignal faultSignal,
            Map<String, String> tags) {
        this.category = category == null ? EventCategory.CORE : category;
        this.operation = operation == null ? "unknown" : operation;
        this.startedAt = startedAt == null ? Instant.now() : startedAt;
        this.endedAt = endedAt == null ? Instant.now() : endedAt;
        this.trafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
        this.faultSignal = faultSignal;
        this.tags = tags == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(tags));
    }

    /** Duration of the observed operation. */
    public Duration duration() {
        return Duration.between(startedAt, endedAt);
    }

    /** Returns the event category. */
    public EventCategory category() {
        return category;
    }

    /** Returns the observed operation. */
    public String operation() {
        return operation;
    }

    /** Returns operation start time. */
    public Instant startedAt() {
        return startedAt;
    }

    /** Returns operation end time. */
    public Instant endedAt() {
        return endedAt;
    }

    /** Returns traffic identity. */
    public TrafficTag trafficTag() {
        return trafficTag;
    }

    /** Returns fault signal, when the event represents a failure. */
    public FaultSignal faultSignal() {
        return faultSignal;
    }

    /** Returns immutable event tags. */
    public Map<String, String> tags() {
        return tags;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NexaryObservationEvent)) {
            return false;
        }
        NexaryObservationEvent that = (NexaryObservationEvent) other;
        return category == that.category
                && operation.equals(that.operation)
                && startedAt.equals(that.startedAt)
                && endedAt.equals(that.endedAt)
                && trafficTag.equals(that.trafficTag)
                && Objects.equals(faultSignal, that.faultSignal)
                && tags.equals(that.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, operation, startedAt, endedAt, trafficTag, faultSignal, tags);
    }

    @Override
    public String toString() {
        return "NexaryObservationEvent[category=" + category
                + ", operation=" + operation
                + ", startedAt=" + startedAt
                + ", endedAt=" + endedAt
                + ", trafficTag=" + trafficTag
                + ", faultSignal=" + faultSignal
                + ", tags=" + tags
                + ']';
    }

    /** Event category. */
    public enum EventCategory { CORE, CACHE, MESSAGING, JOB, SPI, GOVERNANCE }
}
