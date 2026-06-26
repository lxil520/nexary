package org.nexary.governance.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalGovernanceInstanceHealthTest {
    @Test
    void masksNetworkEndpointInstanceKeyByDefault() {
        GovernanceInstanceRef ref = GovernanceInstanceRef.of(
                "downstream:profile-service:lookup",
                "profile-service",
                "10.24.8.9:8080",
                "zone-a");

        assertThat(ref.instanceKey()).startsWith("instance-");
        assertThat(ref.instanceKey()).doesNotContain("10.24.8.9", "8080");
    }

    @Test
    void keepsSmallSamplesHealthyUntilMinimumCalls() {
        LocalGovernanceInstanceHealth health = new LocalGovernanceInstanceHealth(settings(4, 2, 2));
        GovernanceInstanceRef instance = ref("instance-c");
        Instant now = Instant.now();

        health.record(serverError(instance, now));
        health.record(serverError(instance, now.plusMillis(1)));
        health.record(serverError(instance, now.plusMillis(2)));

        InstanceHealthSnapshot snapshot = health.snapshots().get(0);
        assertThat(snapshot.windowCalls()).isEqualTo(3);
        assertThat(snapshot.state()).isEqualTo(InstanceHealthState.HEALTHY);
        assertThat(health.summary().quarantineCandidateCount()).isZero();
    }

    @Test
    void marksRepeatedBadInstanceAsQuarantineCandidate() {
        LocalGovernanceInstanceHealth health = new LocalGovernanceInstanceHealth(settings(4, 2, 2));
        GovernanceInstanceRef healthy = ref("instance-a");
        GovernanceInstanceRef bad = ref("instance-c");
        Instant now = Instant.now();

        for (int i = 0; i < 8; i++) {
            health.record(success(healthy, now.plusMillis(i)));
            health.record(serverError(bad, now.plusMillis(i)));
        }

        assertThat(health.snapshots("downstream:profile-service:lookup"))
                .anySatisfy(snapshot -> {
                    assertThat(snapshot.instanceRef().instanceKey()).isEqualTo("instance-a");
                    assertThat(snapshot.state()).isEqualTo(InstanceHealthState.HEALTHY);
                })
                .anySatisfy(snapshot -> {
                    assertThat(snapshot.instanceRef().instanceKey()).isEqualTo("instance-c");
                    assertThat(snapshot.state()).isEqualTo(InstanceHealthState.QUARANTINE_CANDIDATE);
                    assertThat(snapshot.quarantineReason()).isEqualTo(InstanceQuarantineReason.SERVER_ERROR_RATIO);
                    assertThat(snapshot.recoveryAdvice()).isEqualTo(InstanceRecoveryAdvice.QUARANTINE_CANDIDATE);
                });
        assertThat(health.summary().quarantineCandidateCount()).isEqualTo(1);
        assertThat(health.recentEvents())
                .anySatisfy(event -> {
                    assertThat(event.action()).isEqualTo(GovernanceRuntimeAction.QUARANTINE_CANDIDATE);
                    assertThat(event.instanceHealthState()).isEqualTo(InstanceHealthState.QUARANTINE_CANDIDATE);
                    assertThat(event.quarantineReason()).isEqualTo(InstanceQuarantineReason.SERVER_ERROR_RATIO);
                });
    }

    @Test
    void recordsSpecificTimeoutAndResetReasons() {
        LocalGovernanceInstanceHealth health = new LocalGovernanceInstanceHealth(settings(Duration.ofSeconds(1), 4, 1, 2));
        GovernanceInstanceRef readTimeout = ref("instance-timeout");
        GovernanceInstanceRef reset = ref("instance-reset");
        Instant now = Instant.now();

        for (int i = 0; i < 4; i++) {
            health.record(readTimeout(readTimeout, now.plusMillis(i)));
            health.record(reset(reset, now.plusMillis(i)));
        }

        assertThat(health.snapshots())
                .anySatisfy(snapshot -> {
                    assertThat(snapshot.instanceRef().instanceKey()).isEqualTo("instance-timeout");
                    assertThat(snapshot.quarantineReason()).isEqualTo(InstanceQuarantineReason.READ_TIMEOUT_SPIKE);
                })
                .anySatisfy(snapshot -> {
                    assertThat(snapshot.instanceRef().instanceKey()).isEqualTo("instance-reset");
                    assertThat(snapshot.quarantineReason()).isEqualTo(InstanceQuarantineReason.RESET_SPIKE);
                });
    }

    @Test
    void recoversAfterHealthyProbeWindows() {
        LocalGovernanceInstanceHealth health = new LocalGovernanceInstanceHealth(settings(Duration.ofSeconds(1), 4, 1, 2));
        GovernanceInstanceRef bad = ref("instance-c");
        Instant now = Instant.now();

        for (int i = 0; i < 4; i++) {
            health.record(serverError(bad, now.plusMillis(i)));
        }
        assertThat(health.snapshots().get(0).state()).isEqualTo(InstanceHealthState.QUARANTINE_CANDIDATE);

        health.record(success(bad, now.plusSeconds(2)));
        assertThat(health.snapshots().get(0).state()).isEqualTo(InstanceHealthState.RECOVERING);
        assertThat(health.snapshots().get(0).recoveryAdvice()).isEqualTo(InstanceRecoveryAdvice.RECOVERY_PROBE);

        health.record(success(bad, now.plusSeconds(4)));
        assertThat(health.snapshots().get(0).state()).isEqualTo(InstanceHealthState.HEALTHY);
        assertThat(health.snapshots().get(0).recoveryAdvice()).isEqualTo(InstanceRecoveryAdvice.NONE);
    }

    private static InstanceHealthSettings settings(int minimumCalls, int suspectWindows, int recoveryWindows) {
        return settings(Duration.ofMinutes(10), minimumCalls, suspectWindows, recoveryWindows);
    }

    private static InstanceHealthSettings settings(
            Duration window,
            int minimumCalls,
            int suspectWindows,
            int recoveryWindows) {
        return new InstanceHealthSettings(
                window,
                minimumCalls,
                suspectWindows,
                recoveryWindows,
                Duration.ofMillis(500),
                0.50d,
                0.50d,
                0.50d,
                3.0d);
    }

    private static GovernanceInstanceRef ref(String instanceKey) {
        return GovernanceInstanceRef.of(
                "downstream:profile-service:lookup",
                "profile-service",
                instanceKey,
                "zone-a");
    }

    private static InstanceHealthSignal success(GovernanceInstanceRef ref, Instant timestamp) {
        return new InstanceHealthSignal(
                ref,
                InstanceHealthSignalType.STATUS_CODE_SKEW,
                GovernanceCallOutcome.SUCCESS,
                InstanceStatusCodeClass.HTTP_2XX,
                GovernanceDurationBucket.LT_10_MS,
                timestamp);
    }

    private static InstanceHealthSignal serverError(GovernanceInstanceRef ref, Instant timestamp) {
        return new InstanceHealthSignal(
                ref,
                InstanceHealthSignalType.SERVER_ERROR,
                GovernanceCallOutcome.FAILURE,
                InstanceStatusCodeClass.HTTP_5XX,
                GovernanceDurationBucket.LT_100_MS,
                timestamp);
    }

    private static InstanceHealthSignal readTimeout(GovernanceInstanceRef ref, Instant timestamp) {
        return new InstanceHealthSignal(
                ref,
                InstanceHealthSignalType.READ_TIMEOUT,
                GovernanceCallOutcome.FAILURE,
                InstanceStatusCodeClass.NONE,
                GovernanceDurationBucket.GE_500_MS,
                timestamp);
    }

    private static InstanceHealthSignal reset(GovernanceInstanceRef ref, Instant timestamp) {
        return new InstanceHealthSignal(
                ref,
                InstanceHealthSignalType.CONNECTION_RESET,
                GovernanceCallOutcome.FAILURE,
                InstanceStatusCodeClass.NONE,
                GovernanceDurationBucket.LT_100_MS,
                timestamp);
    }
}
