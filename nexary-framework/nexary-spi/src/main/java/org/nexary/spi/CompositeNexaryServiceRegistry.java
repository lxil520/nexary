package org.nexary.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Combines multiple registries, for example ServiceLoader and a Spring-backed registry. */
public final class CompositeNexaryServiceRegistry implements NexaryServiceRegistry {
    private final List<NexaryServiceRegistry> registries;

    public CompositeNexaryServiceRegistry(List<NexaryServiceRegistry> registries) {
        this.registries = immutableCopy(registries);
    }

    @Override
    public <T> List<T> services(Class<T> type) {
        List<T> services = new ArrayList<>();
        for (NexaryServiceRegistry registry : registries) {
            services.addAll(registry.services(type));
        }
        return Collections.unmodifiableList(services);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
