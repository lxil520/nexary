package org.nexary.samples.governance.api;

import java.time.Instant;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.samples.governance.common.ProfileResult;
import org.nexary.samples.governance.config.GovernanceSampleConfiguration;
import org.nexary.samples.governance.service.ProfileQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry points for the local governance sample. */
@RestController
@RequestMapping("/governance")
public class GovernanceSampleController {
    private final GovernanceRuntime governanceRuntime;
    private final ProfileQueryService profileQueryService;

    public GovernanceSampleController(GovernanceRuntime governanceRuntime, ProfileQueryService profileQueryService) {
        this.governanceRuntime = governanceRuntime;
        this.profileQueryService = profileQueryService;
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
}
