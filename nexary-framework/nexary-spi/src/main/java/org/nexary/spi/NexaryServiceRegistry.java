package org.nexary.spi;

import java.util.List;
import java.util.Optional;

/** Resolves extension implementations without leaking framework-specific lookup mechanics. */
public interface NexaryServiceRegistry {
    /** Returns all services assignable to the requested type. */
    <T> List<T> services(Class<T> type);

    /** Returns the first available service for the requested type. */
    default <T> Optional<T> first(Class<T> type) {
        return services(type).stream().findFirst();
    }
}
