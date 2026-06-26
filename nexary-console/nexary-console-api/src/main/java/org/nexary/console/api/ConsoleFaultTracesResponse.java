package org.nexary.console.api;

import java.util.Collections;
import java.util.List;

/** Read-only console response containing retained local fault traces. */
public final class ConsoleFaultTracesResponse {
    private final List<ConsoleFaultTrace> items;

    /** Creates a trace list response. */
    public ConsoleFaultTracesResponse(List<ConsoleFaultTrace> items) {
        this.items = items == null ? Collections.emptyList() : Collections.unmodifiableList(items);
    }

    /** Returns retained local fault traces. */
    public List<ConsoleFaultTrace> getItems() {
        return items;
    }
}
