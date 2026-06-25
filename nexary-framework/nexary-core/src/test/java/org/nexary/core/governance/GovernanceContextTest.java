package org.nexary.core.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.CancellationContext;
import org.nexary.core.context.CancellationHeaders;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.context.CancellationToken;
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

    @Test
    void scopedContextRestoresPreviousCancellationToken() throws Exception {
        CancellationToken previousToken = CancellationToken.create("previous");
        CancellationToken scopedToken = CancellationToken.create("scoped");
        GovernanceContext previous = GovernanceContext.builder()
                .resource(GovernanceResource.http("previous", "read"))
                .cancellationToken(previousToken)
                .build();
        GovernanceContext scoped = GovernanceContext.builder()
                .resource(GovernanceResource.http("users", "read"))
                .cancellationToken(scopedToken)
                .build();

        GovernanceContext.callWithContext(previous, () -> {
            assertThat(CancellationContext.current()).contains(previousToken);
            GovernanceContext.callWithContext(scoped, () -> {
                assertThat(GovernanceContext.current()).contains(scoped);
                assertThat(CancellationContext.current()).contains(scopedToken);
                return null;
            });
            assertThat(CancellationContext.current()).contains(previousToken);
            return null;
        });

        assertThat(GovernanceContext.current()).isEmpty();
        assertThat(CancellationContext.current()).isEmpty();
    }

    @Test
    void cancellationTokenIsIdempotentAndKeepsFirstReason() {
        CancellationToken token = CancellationToken.create("cancel-1");

        assertThat(token.isCancelled()).isFalse();
        assertThat(token.cancel(CancellationReason.CLIENT_DISCONNECTED)).isTrue();
        assertThat(token.cancel(CancellationReason.SHUTDOWN)).isFalse();

        assertThat(token.isCancelled()).isTrue();
        assertThat(token.reason()).isEqualTo(CancellationReason.CLIENT_DISCONNECTED);
        assertThat(token.cancelledAt()).isPresent();
    }

    @Test
    void cancellationHeadersResolveEarlierDeadlineAndReason() {
        Instant receivedAt = Instant.parse("2026-06-25T00:00:00Z");
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("nexary-deadline-epoch-millis", String.valueOf(receivedAt.plusMillis(500).toEpochMilli()));
        headers.put(CancellationHeaders.TIMEOUT_MILLIS, "1000");
        headers.put(CancellationHeaders.CANCELLATION_ID, "cancel-2");
        headers.put(CancellationHeaders.CANCEL_REASON, "client-disconnected");

        assertThat(CancellationHeaders.deadline(headers, receivedAt)).contains(receivedAt.plusMillis(500));
        assertThat(CancellationHeaders.cancellationId(headers)).contains("cancel-2");
        assertThat(CancellationHeaders.cancellationReason(headers)).isEqualTo(CancellationReason.CLIENT_DISCONNECTED);
    }
}
