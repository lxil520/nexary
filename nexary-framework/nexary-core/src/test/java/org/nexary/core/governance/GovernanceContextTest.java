package org.nexary.core.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.DeadlineContext;
import org.nexary.core.context.TrafficTag;

class GovernanceContextTest {
    @AfterEach
    void tearDown() {
        GovernanceContext.clear();
    }

    @Test
    void derivesPriorityFromTrafficTag() {
        TrafficTag tag = TrafficTag.builder().priority(TrafficTag.Priority.HIGH).build();

        GovernanceContext context = GovernanceContext.builder().trafficTag(tag).build();

        assertThat(context.priority()).isEqualTo(RequestPriority.HIGH);
    }

    @Test
    void scopedContextRestoresPreviousContextAndDeadline() throws Exception {
        Instant previousDeadline = Instant.now().plusSeconds(30);
        Instant scopedDeadline = Instant.now().plusSeconds(5);
        GovernanceContext previous = GovernanceContext.builder()
                .resource(GovernanceResource.http("previous", "read"))
                .deadline(previousDeadline)
                .build();
        GovernanceContext scoped = GovernanceContext.builder()
                .resource(GovernanceResource.http("users", "read"))
                .deadline(scopedDeadline)
                .build();
        GovernanceContext.callWithContext(previous, () -> GovernanceContext.callWithContext(scoped, () -> {
            assertThat(GovernanceContext.current()).contains(scoped);
            assertThat(DeadlineContext.current()).contains(scopedDeadline);
            return null;
        }));

        assertThat(GovernanceContext.current()).isEmpty();
        assertThat(DeadlineContext.current()).isEmpty();
    }
}
