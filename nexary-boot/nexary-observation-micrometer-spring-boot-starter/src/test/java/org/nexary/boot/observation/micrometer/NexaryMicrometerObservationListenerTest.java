package org.nexary.boot.observation.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;

class NexaryMicrometerObservationListenerTest {
    @Test
    void recordsCounterAndTimerWithWhitelistedTagsOnly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NexaryObservationMicrometerProperties properties = new NexaryObservationMicrometerProperties();
        NexaryMicrometerObservationListener listener = new NexaryMicrometerObservationListener(registry, properties);
        Instant startedAt = Instant.parse("2026-06-17T00:00:00Z");
        Instant endedAt = startedAt.plusMillis(25);

        listener.onObservation(new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.CACHE,
                "cache.get",
                startedAt,
                endedAt,
                null,
                null,
                Map.of(
                        "provider", "redis",
                        "outcome", "hit",
                        "tier", "l2",
                        "failure", "none",
                        "cache_key", "user:42",
                        "fencing_token", "99",
                        "exception_message", "boom")));

        Counter counter = registry.find("nexary.observation.events.total")
                .tag("category", "cache")
                .tag("operation", "cache.get")
                .tag("provider", "redis")
                .tag("outcome", "hit")
                .tag("tier", "l2")
                .counter();
        Timer timer = registry.find("nexary.observation.events.duration")
                .tag("category", "cache")
                .tag("operation", "cache.get")
                .tag("provider", "redis")
                .tag("outcome", "hit")
                .tag("tier", "l2")
                .timer();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25.0);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("cache_key"))
                        .noneMatch(tag -> tag.getKey().equals("fencing_token"))
                        .noneMatch(tag -> tag.getKey().equals("exception_message")));
    }

    @Test
    void mapsFailureTagToFailureCategory() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NexaryMicrometerObservationListener listener =
                new NexaryMicrometerObservationListener(registry, new NexaryObservationMicrometerProperties());

        listener.onObservation(new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.CACHE,
                "cache.put",
                Instant.now(),
                Instant.now(),
                null,
                null,
                Map.of("provider", "redis", "outcome", "failure", "failure", "runtime")));

        assertThat(registry.find("nexary.observation.events.total")
                .tag("failure_category", "runtime")
                .counter()).isNotNull();
    }

    @Test
    void nullEventIsNoop() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NexaryMicrometerObservationListener listener =
                new NexaryMicrometerObservationListener(registry, new NexaryObservationMicrometerProperties());

        assertThatCode(() -> listener.onObservation(null)).doesNotThrowAnyException();

        assertThat(registry.getMeters()).isEmpty();
    }
}
