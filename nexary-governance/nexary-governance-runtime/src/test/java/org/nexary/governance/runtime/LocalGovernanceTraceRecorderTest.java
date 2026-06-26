package org.nexary.governance.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.CancellationReason;
import org.nexary.core.governance.GovernanceIsolationReason;
import org.nexary.core.retry.RetryStopReason;

class LocalGovernanceTraceRecorderTest {
    @Test
    void boundsRetainedTracesAndDropsOldest() {
        LocalGovernanceTraceRecorder recorder = new LocalGovernanceTraceRecorder(2, 4, Duration.ofMinutes(1));

        String first = recorder.start("downstream:first:load");
        recorder.record(first, success("downstream:first:load"));
        recorder.finish(first, GovernanceCallOutcome.SUCCESS);
        String second = recorder.start("downstream:second:load");
        recorder.record(second, success("downstream:second:load"));
        recorder.finish(second, GovernanceCallOutcome.SUCCESS);
        String third = recorder.start("downstream:third:load");
        recorder.record(third, success("downstream:third:load"));
        recorder.finish(third, GovernanceCallOutcome.SUCCESS);

        assertThat(recorder.trace(first)).isEmpty();
        assertThat(recorder.traces())
                .extracting(GovernanceFaultTrace::traceKey)
                .containsExactly(second, third);
    }

    @Test
    void boundsStepsPerTrace() {
        LocalGovernanceTraceRecorder recorder = new LocalGovernanceTraceRecorder(4, 2, Duration.ofMinutes(1));
        String traceKey = recorder.start("downstream:profile:load");

        recorder.record(traceKey, success("downstream:profile:first"));
        recorder.record(traceKey, success("downstream:profile:second"));
        recorder.record(traceKey, success("downstream:profile:third"));

        assertThat(recorder.trace(traceKey)).hasValueSatisfying(trace ->
                assertThat(trace.steps())
                        .extracting(GovernanceTraceStep::resourceKey)
                        .containsExactly("downstream:profile:second", "downstream:profile:third"));
    }

    @Test
    void summarizesPrimaryStopReasons() {
        LocalGovernanceTraceRecorder recorder = new LocalGovernanceTraceRecorder(8, 4, Duration.ofMinutes(1));
        String cancelled = recorder.start("http:profile:cancel");
        recorder.record(cancelled, cancelled("http:profile:cancel"));
        recorder.finish(cancelled, GovernanceCallOutcome.CANCELLED);
        String blocked = recorder.start("downstream:orders:send");
        recorder.record(blocked, blocked("downstream:orders:send"));
        recorder.finish(blocked, GovernanceCallOutcome.REJECTED);
        String instance = recorder.start("downstream:profile:instance");
        recorder.record(instance, instanceCandidate("downstream:profile:instance"));
        recorder.finish(instance, GovernanceCallOutcome.NONE);

        GovernanceFaultTraceSummary summary = recorder.summary();

        assertThat(summary.traceCount()).isEqualTo(3);
        assertThat(summary.stoppedCount()).isEqualTo(3);
        assertThat(summary.cancelledCount()).isEqualTo(1);
        assertThat(summary.blockedCount()).isEqualTo(1);
        assertThat(summary.instanceRelatedCount()).isEqualTo(1);
        assertThat(summary.topStopReasons())
                .containsEntry("CANCELLED", 1L)
                .containsEntry("BLOCKED", 1L)
                .containsEntry("INSTANCE_QUARANTINE_CANDIDATE", 1L);
    }

    private static GovernanceTraceStep success(String resourceKey) {
        return new GovernanceTraceStep(
                GovernanceTraceStage.GOVERNANCE,
                resourceKey,
                GovernanceRuntimeAction.EXECUTE,
                GovernanceCallOutcome.SUCCESS,
                GovernanceDurationBucket.LT_10_MS,
                Instant.now(),
                GovernanceRejectionReason.NONE,
                GovernanceBlockReason.NONE,
                CancellationReason.NONE,
                RetryStopReason.NONE,
                GovernanceIsolationReason.NONE,
                InstanceHealthState.HEALTHY,
                InstanceQuarantineReason.NONE);
    }

    private static GovernanceTraceStep cancelled(String resourceKey) {
        return new GovernanceTraceStep(
                GovernanceTraceStage.REQUEST,
                resourceKey,
                GovernanceRuntimeAction.CANCEL,
                GovernanceCallOutcome.CANCELLED,
                GovernanceDurationBucket.NOT_RUN,
                Instant.now(),
                GovernanceRejectionReason.NONE,
                GovernanceBlockReason.NONE,
                CancellationReason.CLIENT_DISCONNECTED,
                RetryStopReason.CLIENT_DISCONNECTED,
                GovernanceIsolationReason.NONE,
                InstanceHealthState.HEALTHY,
                InstanceQuarantineReason.NONE);
    }

    private static GovernanceTraceStep blocked(String resourceKey) {
        return new GovernanceTraceStep(
                GovernanceTraceStage.GOVERNANCE,
                resourceKey,
                GovernanceRuntimeAction.REJECT,
                GovernanceCallOutcome.REJECTED,
                GovernanceDurationBucket.NOT_RUN,
                Instant.now(),
                GovernanceRejectionReason.CIRCUIT_OPEN,
                GovernanceBlockReason.CIRCUIT_OPEN,
                CancellationReason.NONE,
                RetryStopReason.NONE,
                GovernanceIsolationReason.NONE,
                InstanceHealthState.HEALTHY,
                InstanceQuarantineReason.NONE);
    }

    private static GovernanceTraceStep instanceCandidate(String resourceKey) {
        return new GovernanceTraceStep(
                GovernanceTraceStage.INSTANCE_HEALTH,
                resourceKey,
                GovernanceRuntimeAction.QUARANTINE_CANDIDATE,
                GovernanceCallOutcome.NONE,
                GovernanceDurationBucket.NOT_RUN,
                Instant.now(),
                GovernanceRejectionReason.NONE,
                GovernanceBlockReason.NONE,
                CancellationReason.NONE,
                RetryStopReason.NONE,
                GovernanceIsolationReason.NONE,
                InstanceHealthState.QUARANTINE_CANDIDATE,
                InstanceQuarantineReason.SERVER_ERROR_RATIO);
    }
}
