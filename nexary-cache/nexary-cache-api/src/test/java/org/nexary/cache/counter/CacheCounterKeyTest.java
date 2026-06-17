package org.nexary.cache.counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CacheCounterKeyTest {
    @Test
    void buildsQualifiedCounterKey() {
        CacheCounterKey key = CacheCounterKey.of("counter:user", "42");

        assertThat(key.qualified()).isEqualTo("counter:user:42");
    }

    @Test
    void rejectsBlankParts() {
        assertThatThrownBy(() -> CacheCounterKey.of("", "42")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CacheCounterKey.of("counter:user", " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
