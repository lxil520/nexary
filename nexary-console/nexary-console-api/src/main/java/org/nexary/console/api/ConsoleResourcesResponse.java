package org.nexary.console.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Response body for the Nexary console resource list endpoint.
 */
public final class ConsoleResourcesResponse {
    private final List<ConsoleResourceItem> items;

    /**
     * Creates a resource list response.
     */
    public ConsoleResourcesResponse(List<ConsoleResourceItem> items) {
        this.items = items == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
    }

    /** Returns the read-only resource items. */
    public List<ConsoleResourceItem> getItems() {
        return items;
    }
}
