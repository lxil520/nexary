package org.nexary.samples.governance.api;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.GovernanceCallOutcome;
import org.nexary.governance.runtime.GovernanceDurationBucket;
import org.nexary.governance.runtime.GovernanceInstanceHealth;
import org.nexary.governance.runtime.GovernanceInstanceRef;
import org.nexary.governance.runtime.InstanceHealthSignal;
import org.nexary.governance.runtime.InstanceHealthSignalType;
import org.nexary.governance.runtime.InstanceHealthSnapshot;
import org.nexary.governance.runtime.InstanceStatusCodeClass;
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
    private final GovernanceInstanceHealth instanceHealth;
    private final ProfileQueryService profileQueryService;
    private final LocalCircuitBreakerProfileGateway circuitBreakerProfileGateway;

    public GovernanceSampleController(
            GovernanceRuntime governanceRuntime,
            Optional<GovernanceInstanceHealth> instanceHealth,
            ProfileQueryService profileQueryService,
            LocalCircuitBreakerProfileGateway circuitBreakerProfileGateway) {
        this.governanceRuntime = governanceRuntime;
        this.instanceHealth = instanceHealth.orElse(GovernanceInstanceHealth.noop());
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

    @PostMapping("/instance-health/scenario")
    public Map<String, Object> instanceHealthScenario() {
        Instant now = Instant.now();
        for (int index = 0; index < 8; index++) {
            recordInstanceSignal(
                    "instance-a",
                    InstanceHealthSignalType.STATUS_CODE_SKEW,
                    GovernanceCallOutcome.SUCCESS,
                    InstanceStatusCodeClass.HTTP_2XX,
                    GovernanceDurationBucket.LT_10_MS,
                    now.plusMillis(index));
            recordInstanceSignal(
                    "instance-b",
                    InstanceHealthSignalType.SLOW_CALL,
                    GovernanceCallOutcome.SUCCESS,
                    InstanceStatusCodeClass.HTTP_2XX,
                    GovernanceDurationBucket.GE_500_MS,
                    now.plusMillis(index));
            recordInstanceSignal(
                    "instance-c",
                    index % 2 == 0 ? InstanceHealthSignalType.SERVER_ERROR : InstanceHealthSignalType.READ_TIMEOUT,
                    GovernanceCallOutcome.FAILURE,
                    index % 2 == 0 ? InstanceStatusCodeClass.HTTP_5XX : InstanceStatusCodeClass.NONE,
                    GovernanceDurationBucket.GE_500_MS,
                    now.plusMillis(index));
        }
        return instanceHealthBody("scenario");
    }

    @PostMapping("/instance-health/normal")
    public Map<String, Object> instanceHealthNormal() {
        recordInstanceSignal(
                "instance-a",
                InstanceHealthSignalType.STATUS_CODE_SKEW,
                GovernanceCallOutcome.SUCCESS,
                InstanceStatusCodeClass.HTTP_2XX,
                GovernanceDurationBucket.LT_10_MS,
                Instant.now());
        return instanceHealthBody("normal");
    }

    @PostMapping("/instance-health/slow")
    public Map<String, Object> instanceHealthSlow() {
        recordInstanceSignal(
                "instance-b",
                InstanceHealthSignalType.SLOW_CALL,
                GovernanceCallOutcome.SUCCESS,
                InstanceStatusCodeClass.HTTP_2XX,
                GovernanceDurationBucket.GE_500_MS,
                Instant.now());
        return instanceHealthBody("slow");
    }

    @PostMapping("/instance-health/error")
    public Map<String, Object> instanceHealthServerError() {
        recordInstanceSignal(
                "instance-c",
                InstanceHealthSignalType.SERVER_ERROR,
                GovernanceCallOutcome.FAILURE,
                InstanceStatusCodeClass.HTTP_5XX,
                GovernanceDurationBucket.LT_100_MS,
                Instant.now());
        return instanceHealthBody("server_error");
    }

    @PostMapping("/instance-health/read-timeout")
    public Map<String, Object> instanceHealthReadTimeout() {
        recordInstanceSignal(
                "instance-c",
                InstanceHealthSignalType.READ_TIMEOUT,
                GovernanceCallOutcome.FAILURE,
                InstanceStatusCodeClass.NONE,
                GovernanceDurationBucket.GE_500_MS,
                Instant.now());
        return instanceHealthBody("read_timeout");
    }

    private void recordInstanceSignal(
            String instanceKey,
            InstanceHealthSignalType signalType,
            GovernanceCallOutcome outcome,
            InstanceStatusCodeClass statusCodeClass,
            GovernanceDurationBucket durationBucket,
            Instant timestamp) {
        GovernanceInstanceRef ref = GovernanceInstanceRef.of(
                GovernanceSampleConfiguration.INSTANCE_HEALTH_RESOURCE.key(),
                "profile-service",
                instanceKey,
                "zone-a");
        instanceHealth.record(new InstanceHealthSignal(
                ref,
                signalType,
                outcome,
                statusCodeClass,
                durationBucket,
                timestamp));
    }

    private Map<String, Object> instanceHealthBody(String scenario) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scenario", scenario);
        body.put("resourceKey", GovernanceSampleConfiguration.INSTANCE_HEALTH_RESOURCE.key());
        body.put("summary", Map.of(
                "instanceCount", instanceHealth.summary().instanceCount(),
                "healthyCount", instanceHealth.summary().healthyCount(),
                "suspectCount", instanceHealth.summary().suspectCount(),
                "quarantineCandidateCount", instanceHealth.summary().quarantineCandidateCount(),
                "recoveringCount", instanceHealth.summary().recoveringCount(),
                "recoveryProbeCount", instanceHealth.summary().recoveryProbeCount()));
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (InstanceHealthSnapshot snapshot : instanceHealth.snapshots(GovernanceSampleConfiguration.INSTANCE_HEALTH_RESOURCE.key())) {
            snapshots.add(snapshotBody(snapshot));
        }
        body.put("snapshots", snapshots);
        return body;
    }

    private Map<String, Object> snapshotBody(InstanceHealthSnapshot snapshot) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("instanceKey", snapshot.instanceRef().instanceKey());
        body.put("state", snapshot.state().name());
        body.put("quarantineReason", snapshot.quarantineReason().name());
        body.put("recoveryAdvice", snapshot.recoveryAdvice().name());
        body.put("windowCalls", snapshot.windowCalls());
        body.put("failureCount", snapshot.failureCount());
        body.put("slowCallCount", snapshot.slowCallCount());
        body.put("timeoutCount", snapshot.timeoutCount());
        body.put("serverErrorCount", snapshot.serverErrorCount());
        return body;
    }
}
