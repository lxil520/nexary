package org.nexary.boot.observation.micrometer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceObservationEvents;
import org.nexary.core.governance.GovernanceResource;
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
    void mapsGovernanceEventsWithBoundedLabelsOnly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NexaryMicrometerObservationListener listener =
                new NexaryMicrometerObservationListener(registry, new NexaryObservationMicrometerProperties());
        Instant startedAt = Instant.parse("2026-06-19T00:00:00Z");
        TrafficTag trafficTag = TrafficTag.builder()
                .channel(TrafficTag.Channel.OFFLINE)
                .priority(TrafficTag.Priority.HIGH)
                .tenant("tenant-42")
                .bizKey("order-99")
                .build();

        listener.onObservation(GovernanceObservationEvents.rateLimited(
                GovernanceResource.http("profile-api", "get-profile"),
                trafficTag,
                startedAt,
                startedAt.plusMillis(12)));

        Counter counter = registry.find("nexary.observation.events.total")
                .tag("category", "governance")
                .tag("operation", "governance.rate_limited")
                .tag("provider", "nexary")
                .tag("outcome", "rejected")
                .tag("failure_category", "rate_limited")
                .tag("resource_kind", "http")
                .tag("governance_action", "rate_limited")
                .tag("traffic_channel", "offline")
                .tag("traffic_priority", "high")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("resource"))
                        .noneMatch(tag -> tag.getKey().equals("tenant"))
                        .noneMatch(tag -> tag.getKey().equals("biz_key")));
    }

    @Test
    void dropsHighCardinalityGovernanceFieldsEvenWhenEventContainsThem() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NexaryMicrometerObservationListener listener =
                new NexaryMicrometerObservationListener(registry, new NexaryObservationMicrometerProperties());
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("provider", "nexary");
        tags.put("outcome", "rejected");
        tags.put("governance_action", "bulkhead_rejected");
        tags.put("resource_kind", "downstream");
        tags.put("resource", "profile-api-user-42");
        tags.put("resource_name", "profile-api-user-42");
        tags.put("tenant", "tenant-42");
        tags.put("biz_key", "order-99");
        tags.put("message_id", "msg-123");
        tags.put("execution_id", "exec-456");
        tags.put("payload", "{\"userId\":\"42\"}");
        tags.put("exception_message", "raw downstream failure");
        tags.put("stack_trace", "java.lang.IllegalStateException: raw");

        listener.onObservation(new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.GOVERNANCE,
                "governance.bulkhead.rejected",
                now,
                now.plusMillis(1),
                null,
                null,
                tags));

        assertThat(registry.find("nexary.observation.events.total")
                .tag("governance_action", "bulkhead_rejected")
                .tag("resource_kind", "downstream")
                .counter()).isNotNull();
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("resource"))
                        .noneMatch(tag -> tag.getKey().equals("resource_name"))
                        .noneMatch(tag -> tag.getKey().equals("tenant"))
                        .noneMatch(tag -> tag.getKey().equals("biz_key"))
                        .noneMatch(tag -> tag.getKey().equals("message_id"))
                        .noneMatch(tag -> tag.getKey().equals("execution_id"))
                        .noneMatch(tag -> tag.getKey().equals("payload"))
                        .noneMatch(tag -> tag.getKey().equals("exception_message"))
                        .noneMatch(tag -> tag.getKey().equals("stack_trace")));
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
