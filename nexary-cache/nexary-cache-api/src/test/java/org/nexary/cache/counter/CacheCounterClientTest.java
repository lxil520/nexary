package org.nexary.cache.counter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class CacheCounterClientTest {
    @Test
    void decrementDelegatesToNegativeIncrement() {
        RecordingCounterClient client = new RecordingCounterClient();
        CacheCounterKey key = CacheCounterKey.of("counter:user", "42");

        CacheCounterMutation result = client.decrement(key, 3, Duration.ofSeconds(5));

        assertThat(result.value()).isEqualTo(-3);
        assertThat(client.lastDelta).isEqualTo(-3);
        assertThat(client.lastTtl).isEqualTo(Duration.ofSeconds(5));
    }

    private static final class RecordingCounterClient implements CacheCounterClient {
        private long value;
        private long lastDelta;
        private Duration lastTtl;

        @Override
        public CacheCounterMutation increment(CacheCounterKey key, long delta, Duration ttlOnCreate) {
            lastDelta = delta;
            lastTtl = ttlOnCreate;
            value += delta;
            return new CacheCounterMutation(key, value, value == delta, ttlOnCreate != null);
        }

        @Override
        public OptionalLong current(CacheCounterKey key) {
            return OptionalLong.of(value);
        }

        @Override
        public boolean clear(CacheCounterKey key) {
            value = 0;
            return true;
        }
    }
}
