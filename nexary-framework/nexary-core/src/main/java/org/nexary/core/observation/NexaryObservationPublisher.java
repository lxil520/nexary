package org.nexary.core.observation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Publishes provider-neutral observation events emitted by Nexary capabilities. */
@FunctionalInterface
public interface NexaryObservationPublisher {
    /** Publishes an observation event. Implementations should not throw into business paths. */
    void publish(NexaryObservationEvent event);

    /** Returns a no-op publisher for users that do not configure observation listeners. */
    static NexaryObservationPublisher noop() {
        return event -> {
        };
    }

    /** Returns a publisher that fans out to listeners and shields business paths from listener failures. */
    static NexaryObservationPublisher fanOut(Collection<NexaryObservationListener> listeners) {
        List<NexaryObservationListener> safeListeners = listeners == null
                ? List.of()
                : listeners.stream().filter(Objects::nonNull).toList();
        if (safeListeners.isEmpty()) {
            return noop();
        }
        return event -> {
            for (NexaryObservationListener listener : safeListeners) {
                try {
                    listener.onObservation(event);
                } catch (RuntimeException ignored) {
                    // Observation must not break user code.
                }
            }
        };
    }
}
