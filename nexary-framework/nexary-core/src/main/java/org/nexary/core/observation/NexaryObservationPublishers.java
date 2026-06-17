package org.nexary.core.observation;

import java.util.List;

/** Factory methods for safe observation publishers. */
public final class NexaryObservationPublishers {
    private NexaryObservationPublishers() {
    }

    /** Creates a publisher that fans out to listeners and swallows listener failures. */
    public static NexaryObservationPublisher composite(List<NexaryObservationListener> listeners) {
        List<NexaryObservationListener> safeListeners = listeners == null ? List.of() : List.copyOf(listeners);
        if (safeListeners.isEmpty()) {
            return NexaryObservationPublisher.noop();
        }
        return event -> safeListeners.forEach(listener -> {
            try {
                listener.onObservation(event);
            } catch (RuntimeException ignored) {
                // Observation must not change functional behavior.
            }
        });
    }
}
