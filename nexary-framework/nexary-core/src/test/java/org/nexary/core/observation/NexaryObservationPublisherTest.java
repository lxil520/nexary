package org.nexary.core.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NexaryObservationPublisherTest {
    @Test
    void noopPublisherDoesNotThrow() {
        assertThatCode(() -> NexaryObservationPublisher.noop().publish(event("cache.get", Map.of())))
                .doesNotThrowAnyException();
    }

    @Test
    void fanOutDeliversEventsAndShieldsListenerFailures() {
        List<NexaryObservationEvent> received = new ArrayList<>();
        NexaryObservationPublisher publisher = NexaryObservationPublisher.fanOut(List.of(
                event -> {
                    throw new IllegalStateException("listener failed");
                },
                received::add));
        NexaryObservationEvent event = event("cache.put", Map.of("outcome", "success"));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();

        assertThat(received).containsExactly(event);
    }

    @Test
    void eventTagsAreDefensivelyCopied() {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("capability", "cache");
        NexaryObservationEvent event = event("cache.get", tags);

        tags.put("cache_key", "must-not-leak");

        assertThat(event.tags()).containsOnly(Map.entry("capability", "cache"));
    }

    private static NexaryObservationEvent event(String operation, Map<String, String> tags) {
        Instant now = Instant.now();
        return new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.CACHE,
                operation,
                now,
                now,
                null,
                null,
                tags);
    }
}
