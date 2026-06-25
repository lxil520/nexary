package org.nexary.samples.governance.sentinel.api;

import java.time.Duration;
import java.time.Instant;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.retry.RetrySignal;
import org.nexary.core.retry.RetryStopReason;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.GovernanceRejectedException;
import org.nexary.samples.governance.sentinel.config.SentinelGovernanceSampleResources;
import org.nexary.samples.governance.sentinel.service.SentinelGovernanceSampleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry points for the Sentinel provider sample. */
@RestController
@RequestMapping("/governance/sentinel")
public class SentinelGovernanceSampleController {
    private final GovernanceRuntime governanceRuntime;
    private final SentinelGovernanceSampleService service;

    public SentinelGovernanceSampleController(
            GovernanceRuntime governanceRuntime,
            SentinelGovernanceSampleService service) {
        this.governanceRuntime = governanceRuntime;
        this.service = service;
    }

    /** Uses a low QPS policy so repeated calls are blocked by Sentinel. */
    @GetMapping("/rate")
    public SentinelSampleResult rate() throws Exception {
        return governanceRuntime.execute(
                GovernanceContext.builder()
                        .resource(SentinelGovernanceSampleResources.RATE_RESOURCE)
                        .deadline(Instant.now().plusSeconds(2))
                        .build(),
                service::ok,
                service::fallback);
    }

    /** Uses Sentinel thread-count flow control; call concurrently to observe BULKHEAD_FULL. */
    @GetMapping("/bulkhead")
    public SentinelSampleResult bulkhead(@RequestParam(defaultValue = "500") long holdMillis) throws Exception {
        return governanceRuntime.execute(
                GovernanceContext.builder()
                        .resource(SentinelGovernanceSampleResources.BULKHEAD_RESOURCE)
                        .deadline(Instant.now().plusSeconds(5))
                        .build(),
                () -> service.hold(Duration.ofMillis(Math.max(25L, Math.min(holdMillis, 5000L)))),
                service::fallback);
    }

    /** Records slow calls so Sentinel can open the slow-call circuit. */
    @GetMapping("/slow")
    public SentinelSampleResult slow(@RequestParam(defaultValue = "150") long durationMillis) throws Exception {
        return governanceRuntime.execute(
                GovernanceContext.builder()
                        .resource(SentinelGovernanceSampleResources.SLOW_RESOURCE)
                        .deadline(Instant.now().plusSeconds(5))
                        .build(),
                () -> service.hold(Duration.ofMillis(Math.max(25L, Math.min(durationMillis, 5000L)))),
                service::fallback);
    }

    /** Records failures so Sentinel can open the exception circuit. */
    @GetMapping("/failure")
    public SentinelSampleResult failure() throws Exception {
        return governanceRuntime.execute(
                GovernanceContext.builder()
                        .resource(SentinelGovernanceSampleResources.FAILURE_RESOURCE)
                        .deadline(Instant.now().plusSeconds(2))
                        .build(),
                service::fail,
                service::fallback);
    }

    /** Demonstrates Nexary degraded fallback before entering Sentinel. */
    @GetMapping("/fallback")
    public SentinelSampleResult fallback() throws Exception {
        return governanceRuntime.execute(
                GovernanceContext.builder()
                        .resource(SentinelGovernanceSampleResources.DEGRADED_RESOURCE)
                        .deadline(Instant.now().plusSeconds(2))
                        .build(),
                service::ok,
                service::fallback);
    }

    /** Demonstrates that a retry loop stops when governance returns a STOP retry signal. */
    @GetMapping("/retry-stop")
    public SentinelSampleResult retryStop(@RequestParam(defaultValue = "3") int maxAttempts) throws Exception {
        int boundedAttempts = Math.max(1, Math.min(maxAttempts, 5));
        for (int attempt = 1; attempt <= boundedAttempts; attempt++) {
            try {
                governanceRuntime.execute(
                        GovernanceContext.builder()
                                .resource(SentinelGovernanceSampleResources.RATE_RESOURCE)
                                .deadline(Instant.now().plusSeconds(2))
                                .build(),
                        service::ok);
            } catch (GovernanceRejectedException ex) {
                RetrySignal signal = ex.decision() == null ? null : ex.decision().retrySignal();
                RetryStopReason reason = signal == null ? RetryStopReason.UNKNOWN : signal.stopReason();
                return new SentinelSampleResult("retry-stop", "stopped", attempt, reason.name());
            }
        }
        return new SentinelSampleResult("retry-stop", "completed", boundedAttempts, "NONE");
    }
}
