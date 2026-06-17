package org.nexary.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeNexaryServiceRegistryTest {
    @Test
    void combinesRegistriesInOrder() {
        NexaryServiceRegistry first = new NexaryServiceRegistry() {
            @Override
            public <T> List<T> services(Class<T> type) {
                return List.of(type.cast("a"));
            }
        };
        NexaryServiceRegistry second = new NexaryServiceRegistry() {
            @Override
            public <T> List<T> services(Class<T> type) {
                return List.of(type.cast("b"));
            }
        };

        CompositeNexaryServiceRegistry registry = new CompositeNexaryServiceRegistry(List.of(first, second));

        assertThat(registry.services(String.class)).containsExactly("a", "b");
    }
}
