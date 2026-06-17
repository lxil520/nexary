package org.nexary.spi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

/** Service registry backed by the JDK ServiceLoader. */
public final class ServiceLoaderNexaryServiceRegistry implements NexaryServiceRegistry {
    @Override
    public <T> List<T> services(Class<T> type) {
        List<T> services = new ArrayList<>();
        ServiceLoader.load(type).forEach(services::add);
        return Collections.unmodifiableList(services);
    }
}
