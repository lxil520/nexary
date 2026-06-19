package org.nexary.core.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeoutDecisionTest {
    @Test
    void allowsContextWithoutDeadline() {
        TimeoutDecision decision = TimeoutDecision.from(GovernanceContext.builder().build(), Instant.now());

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("allowed");
    }

    @Test
    void rejectsExpiredDeadline() {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        GovernanceContext context = GovernanceContext.builder().deadline(now.minusMillis(1)).build();

        TimeoutDecision decision = TimeoutDecision.from(context, now);

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("deadline_exceeded");
    }
}
