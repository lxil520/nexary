package org.nexary.samples.governance.config;

import org.nexary.core.governance.GovernanceResource;
import org.springframework.context.annotation.Configuration;

/** Stable resource names used by the governance sample. */
@Configuration
public class GovernanceSampleConfiguration {
    public static final GovernanceResource PROFILE_RESOURCE = GovernanceResource.http("profile-api", "get-profile");
    public static final GovernanceResource CANCELLATION_RESOURCE = GovernanceResource.http("profile-api", "slow-profile");
    public static final GovernanceResource DEGRADED_RESOURCE = GovernanceResource.downstream("inventory-service", "reserve");
    public static final GovernanceResource CIRCUIT_RESOURCE = GovernanceResource.downstream("profile-service", "load-profile");
    public static final GovernanceResource INSTANCE_HEALTH_RESOURCE =
            GovernanceResource.downstream("profile-service", "instance-health");
}
