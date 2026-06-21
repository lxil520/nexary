package org.nexary.samples.governance.config;

import org.nexary.core.governance.GovernanceResource;
import org.springframework.context.annotation.Configuration;

/** Stable resource names used by the governance sample. */
@Configuration
public class GovernanceSampleConfiguration {
    public static final GovernanceResource PROFILE_RESOURCE = GovernanceResource.http("profile-api", "get-profile");
    public static final GovernanceResource DEGRADED_RESOURCE = GovernanceResource.downstream("inventory-service", "reserve");
}
