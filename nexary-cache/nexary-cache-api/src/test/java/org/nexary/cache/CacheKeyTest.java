package org.nexary.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CacheKeyTest {
    @Test
    void createsQualifiedKey() {
        assertThat(CacheKey.of("user", "42").qualified()).isEqualTo("user:42");
    }

    @Test
    void rejectsBlankNamespace() {
        assertThatThrownBy(() -> CacheKey.of(" ", "42")).isInstanceOf(IllegalArgumentException.class);
    }
}
