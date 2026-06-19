package org.nexary.samples.governance.config;

import java.time.Duration;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernancePolicyRegistry;
import org.nexary.governance.runtime.LocalGovernancePolicyRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Sample local governance policies. */
@Configuration
public class GovernanceSampleConfiguration {
    public static final GovernanceResource PROFILE_RESOURCE = GovernanceResource.http("profile-api", "get-profile");
    public static final GovernanceResource DEGRADED_RESOURCE = GovernanceResource.downstream("inventory-service", "reserve");

    @Bean
    public GovernancePolicyRegistry governancePolicyRegistry() {
        return LocalGovernancePolicyRegistry.builder()
                .policy(PROFILE_RESOURCE, GovernancePolicy.builder()
                        .maxRequestsPerWindow(2)
                        .rateLimitWindow(Duration.ofMinutes(1))
                        .maxConcurrency(1)
                        .build())
                .policy(DEGRADED_RESOURCE, GovernancePolicy.builder()
                        .degraded(true)
                        .build())
                .build();
    }
}
