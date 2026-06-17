package org.nexary.core.observation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Factory methods for safe observation publishers. */
public final class NexaryObservationPublishers {
    private NexaryObservationPublishers() {
    }

    /** Creates a publisher that fans out to listeners and swallows listener failures. */
    public static NexaryObservationPublisher composite(List<NexaryObservationListener> listeners) {
        List<NexaryObservationListener> safeListeners = immutableListeners(listeners);
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

    private static List<NexaryObservationListener> immutableListeners(List<NexaryObservationListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(listeners));
    }
}
