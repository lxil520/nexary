package org.nexary.core.observation;

/** Listener for provider-neutral Nexary observation events. */
@FunctionalInterface
public interface NexaryObservationListener {
    /** Receives an observation event. */
    void onObservation(NexaryObservationEvent event);
}
