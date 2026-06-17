package org.nexary.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeadlineContextTest {
    @AfterEach
    void tearDown() {
        DeadlineContext.clear();
    }

    @Test
    void scopedDeadlineRestoresPreviousValue() throws Exception {
        Instant previous = Instant.now().plusSeconds(10);
        Instant scoped = Instant.now().plusSeconds(1);
        DeadlineContext.set(previous);

        Instant result = DeadlineContext.callWithDeadline(scoped, () -> DeadlineContext.current().orElseThrow());

        assertThat(result).isEqualTo(scoped);
        assertThat(DeadlineContext.current()).contains(previous);
    }
}
