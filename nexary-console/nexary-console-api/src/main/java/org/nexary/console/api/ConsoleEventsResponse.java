package org.nexary.console.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response body for the Nexary console recent events endpoint.
 */
public final class ConsoleEventsResponse {
    private final List<ConsoleEventItem> items;

    /**
     * Creates an event list response.
     */
    public ConsoleEventsResponse(List<ConsoleEventItem> items) {
        this.items = items == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** Returns recent low-cardinality governance events. */
    public List<ConsoleEventItem> getItems() {
        return items;
    }
}
