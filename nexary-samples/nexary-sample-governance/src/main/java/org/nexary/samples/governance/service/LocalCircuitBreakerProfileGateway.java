package org.nexary.samples.governance.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.governance.runtime.GovernanceCircuitState;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernanceRejectionReason;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.LocalGovernancePolicyRegistry;
import org.nexary.governance.runtime.LocalGovernanceRuntime;
import org.nexary.samples.governance.common.CircuitProfileResult;
import org.nexary.samples.governance.common.ProfileResult;
import org.nexary.samples.governance.config.GovernanceSampleConfiguration;
import org.springframework.stereotype.Service;

/** Runs the governance sample circuit path through the local runtime. */
@Service
public class LocalCircuitBreakerProfileGateway {
    public static final Duration OPEN_STATE_DURATION = Duration.ofMillis(150);

    private static final Duration SLOW_CALL_DURATION = Duration.ofMillis(100);
    private static final String PRIORITY = "normal";

    private final ProfileQueryService profileQueryService;
    private LocalGovernanceRuntime runtime;

    public LocalCircuitBreakerProfileGateway(ProfileQueryService profileQueryService) {
        this.profileQueryService = profileQueryService;
        reset();
    }

    public synchronized CircuitProfileResult callProfile(String userId, String mode) throws Exception {
        GovernanceCircuitState before = snapshot().circuitState();
        try {
            ProfileResult profile = runtime.execute(
                    context(),
                    () -> invoke(userId, mode),
                    () -> profileQueryService.fallbackProfile(userId));
            GovernanceRuntimeSnapshot after = snapshot();
            return new CircuitProfileResult(profile, outcome(profile, mode, before, after), after);
        } catch (RuntimeException ex) {
            GovernanceRuntimeSnapshot after = snapshot();
            return new CircuitProfileResult(
                    profileQueryService.fallbackProfile(userId),
                    failureOutcome(before, after),
                    after);
        }
    }

    public synchronized GovernanceRuntimeSnapshot snapshot() {
        List<GovernanceRuntimeSnapshot> snapshots = runtime.snapshots();
        for (GovernanceRuntimeSnapshot snapshot : snapshots) {
            if (GovernanceSampleConfiguration.CIRCUIT_RESOURCE.key().equals(snapshot.resourceKey())) {
                return snapshot;
            }
        }
        return emptySnapshot();
    }

    public synchronized GovernanceRuntimeSnapshot reset() {
        runtime = new LocalGovernanceRuntime(
                LocalGovernancePolicyRegistry.builder()
                        .policy(GovernanceSampleConfiguration.CIRCUIT_RESOURCE, circuitPolicy())
                        .build(),
                null);
        return emptySnapshot();
    }

    private ProfileResult invoke(String userId, String mode) throws Exception {
        if ("failure".equalsIgnoreCase(mode)) {
            return profileQueryService.failProfile(userId);
        }
        if ("slow".equalsIgnoreCase(mode)) {
            return profileQueryService.slowProfile(userId);
        }
        return profileQueryService.loadProfile(userId);
    }

    private GovernanceContext context() {
        return GovernanceContext.builder()
                .resource(GovernanceSampleConfiguration.CIRCUIT_RESOURCE)
                .trafficTag(TrafficTag.builder()
                        .channel(TrafficTag.Channel.ONLINE)
                        .priority(TrafficTag.Priority.NORMAL)
                        .build())
                .deadline(Instant.now().plusSeconds(2))
                .build();
    }

    private GovernancePolicy circuitPolicy() {
        return GovernancePolicy.builder()
                .minimumRequests(2)
                .failureRateThreshold(100.0d)
                .slowCallThreshold(100.0d)
                .slowCallDuration(SLOW_CALL_DURATION)
                .openStateDuration(OPEN_STATE_DURATION)
                .halfOpenMaxCalls(1)
                .slidingWindowSize(8)
                .slidingWindowDuration(Duration.ofSeconds(5))
                .consecutiveFailureThreshold(2)
                .build();
    }

    private String outcome(
            ProfileResult profile,
            String mode,
            GovernanceCircuitState before,
            GovernanceRuntimeSnapshot after) {
        if ("fallback".equals(profile.source())) {
            return "fallback_open";
        }
        if (before == GovernanceCircuitState.HALF_OPEN && after.circuitState() == GovernanceCircuitState.CLOSED) {
            return "half_open_recovered";
        }
        if ("slow".equalsIgnoreCase(mode) && after.circuitState() == GovernanceCircuitState.OPEN) {
            return "slow_opened";
        }
        if ("slow".equalsIgnoreCase(mode)) {
            return "slow_recorded";
        }
        return "primary";
    }

    private String failureOutcome(GovernanceCircuitState before, GovernanceRuntimeSnapshot after) {
        if (before == GovernanceCircuitState.HALF_OPEN && after.circuitState() == GovernanceCircuitState.OPEN) {
            return "half_open_reopened";
        }
        if (after.circuitState() == GovernanceCircuitState.OPEN) {
            return "failure_opened";
        }
        return "fallback_after_failure";
    }

    private GovernanceRuntimeSnapshot emptySnapshot() {
        return new GovernanceRuntimeSnapshot(
                GovernanceSampleConfiguration.CIRCUIT_RESOURCE.key(),
                PRIORITY,
                GovernanceCircuitState.CLOSED,
                0,
                0,
                0,
                0,
                0L,
                GovernanceRejectionReason.NONE,
                null);
    }
}
