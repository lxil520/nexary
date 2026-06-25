package org.nexary.samples.governance.api;

import java.time.Duration;
import java.time.Instant;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.samples.governance.common.CircuitProfileResult;
import org.nexary.samples.governance.common.ProfileResult;
import org.nexary.samples.governance.config.GovernanceSampleConfiguration;
import org.nexary.samples.governance.service.LocalCircuitBreakerProfileGateway;
import org.nexary.samples.governance.service.ProfileQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry points for the local governance sample. */
@RestController
@RequestMapping("/governance")
public class GovernanceSampleController {
    private final GovernanceRuntime governanceRuntime;
    private final ProfileQueryService profileQueryService;
    private final LocalCircuitBreakerProfileGateway circuitBreakerProfileGateway;

    public GovernanceSampleController(
            GovernanceRuntime governanceRuntime,
            ProfileQueryService profileQueryService,
            LocalCircuitBreakerProfileGateway circuitBreakerProfileGateway) {
        this.governanceRuntime = governanceRuntime;
        this.profileQueryService = profileQueryService;
        this.circuitBreakerProfileGateway = circuitBreakerProfileGateway;
    }

    @GetMapping("/profiles/{userId}")
    public ProfileResult profile(@PathVariable String userId) throws Exception {
        GovernanceContext context = GovernanceContext.builder()
                .resource(GovernanceSampleConfiguration.PROFILE_RESOURCE)
                .trafficTag(TrafficTag.builder()
                        .channel(TrafficTag.Channel.ONLINE)
                        .priority(TrafficTag.Priority.NORMAL)
                        .tenant("demo")
                        .bizKey("profile")
                        .build())
                .deadline(Instant.now().plusMillis(300))
                .build();
        return governanceRuntime.execute(
                context,
                () -> profileQueryService.loadProfile(userId),
                () -> profileQueryService.fallbackProfile(userId));
    }

    @GetMapping("/degraded/{userId}")
    public ProfileResult degraded(@PathVariable String userId) throws Exception {
        GovernanceContext context = GovernanceContext.builder()
                .resource(GovernanceSampleConfiguration.DEGRADED_RESOURCE)
                .trafficTag(TrafficTag.defaults())
                .deadline(Instant.now().plusMillis(300))
                .build();
        return governanceRuntime.execute(
                context,
                () -> profileQueryService.loadProfile(userId),
                () -> profileQueryService.fallbackProfile(userId));
    }

    @GetMapping("/cancellation/slow/{userId}")
    public ProfileResult cancellableSlowProfile(
            @PathVariable String userId,
            @RequestParam(defaultValue = "3000") long durationMillis) throws Exception {
        Duration duration = Duration.ofMillis(Math.max(25L, Math.min(durationMillis, 30_000L)));
        GovernanceContext context = GovernanceContext.builder()
                .resource(GovernanceSampleConfiguration.CANCELLATION_RESOURCE)
                .trafficTag(TrafficTag.defaults())
                .deadline(Instant.now().plus(duration).plusMillis(500))
                .build();
        return governanceRuntime.execute(
                context,
                () -> profileQueryService.cancellableSlowProfile(userId, duration),
                () -> profileQueryService.fallbackProfile(userId));
    }

    @GetMapping("/circuit/profiles/{userId}")
    public CircuitProfileResult circuitProfile(
            @PathVariable String userId,
            @RequestParam(defaultValue = "success") String mode) throws Exception {
        return circuitBreakerProfileGateway.callProfile(userId, mode);
    }

    @GetMapping("/circuit/state")
    public GovernanceRuntimeSnapshot circuitState() {
        return circuitBreakerProfileGateway.snapshot();
    }

    @PostMapping("/circuit/reset")
    public GovernanceRuntimeSnapshot resetCircuit() {
        return circuitBreakerProfileGateway.reset();
    }
}
