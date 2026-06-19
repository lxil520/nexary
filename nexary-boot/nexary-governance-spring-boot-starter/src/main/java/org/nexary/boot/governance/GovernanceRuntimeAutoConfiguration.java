package org.nexary.boot.governance;

import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernancePolicyRegistry;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.LocalGovernancePolicyRegistry;
import org.nexary.governance.runtime.LocalGovernanceRuntime;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for the local Nexary governance runtime. */
@AutoConfiguration
@EnableConfigurationProperties(GovernanceRuntimeProperties.class)
@ConditionalOnProperty(prefix = "nexary.governance.runtime", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GovernanceRuntimeAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public GovernancePolicyRegistry nexaryGovernancePolicyRegistry(GovernanceRuntimeProperties properties) {
        GovernancePolicy defaultPolicy = GovernancePolicy.builder()
                .maxRequestsPerWindow(properties.getDefaultPolicy().getMaxRequestsPerWindow())
                .rateLimitWindow(properties.getDefaultPolicy().getRateLimitWindow())
                .maxConcurrency(properties.getDefaultPolicy().getMaxConcurrency())
                .degraded(properties.getDefaultPolicy().isDegraded())
                .build();
        return LocalGovernancePolicyRegistry.builder().defaultPolicy(defaultPolicy).build();
    }

    @Bean
    @ConditionalOnMissingBean
    public GovernanceRuntime nexaryGovernanceRuntime(
            GovernancePolicyRegistry policyRegistry,
            GovernanceRuntimeProperties properties,
            java.util.Optional<NexaryObservationPublisher> observationPublisher) {
        NexaryObservationPublisher publisher = observationPublisher.orElse(NexaryObservationPublisher.noop());
        return new LocalGovernanceRuntime(policyRegistry, publisher);
    }
}
