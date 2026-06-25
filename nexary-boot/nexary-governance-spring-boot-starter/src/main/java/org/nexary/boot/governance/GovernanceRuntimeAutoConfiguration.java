package org.nexary.boot.governance;

import java.util.Map;
import org.nexary.core.governance.GovernanceExecution;
import org.nexary.core.governance.GovernancePriority;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernancePolicyRegistry;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.LocalGovernancePolicyRegistry;
import org.nexary.governance.runtime.LocalGovernanceRuntime;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
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
        LocalGovernancePolicyRegistry.Builder builder = LocalGovernancePolicyRegistry.builder()
                .defaultPolicy(toPolicy(defaultPolicy(properties)));
        for (Map.Entry<String, GovernanceRuntimeProperties.ResourcePolicy> entry : properties.getResources().entrySet()) {
            GovernanceRuntimeProperties.ResourcePolicy resourcePolicy = entry.getValue() == null
                    ? new GovernanceRuntimeProperties.ResourcePolicy()
                    : entry.getValue();
            GovernanceResource resource = toResource(entry.getKey(), resourcePolicy);
            builder.policy(resource, toPolicy(resourcePolicy));
            for (Map.Entry<GovernancePriority, GovernanceRuntimeProperties.Policy> priority
                    : resourcePolicy.getPriorities().entrySet()) {
                builder.policy(resource, priority.getKey(), toPolicy(priority.getValue()));
            }
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "nexary.governance", name = "provider", havingValue = "local", matchIfMissing = true)
    public GovernanceRuntime nexaryGovernanceRuntime(
            GovernancePolicyRegistry policyRegistry,
            GovernanceRuntimeProperties properties,
            java.util.Optional<NexaryObservationPublisher> observationPublisher) {
        NexaryObservationPublisher publisher = observationPublisher.orElse(NexaryObservationPublisher.noop());
        return new LocalGovernanceRuntime(policyRegistry, publisher);
    }

    @Bean
    @ConditionalOnBean(GovernanceRuntime.class)
    @ConditionalOnMissingBean
    public GovernanceExecution nexaryGovernanceExecution(GovernanceRuntime governanceRuntime) {
        return (context, action) -> governanceRuntime.execute(context, () -> action.call());
    }

    private static GovernanceRuntimeProperties.Policy defaultPolicy(GovernanceRuntimeProperties properties) {
        if (properties.getRuntime().getDefaultPolicy() != null) {
            return properties.getRuntime().getDefaultPolicy();
        }
        return properties.getDefaultPolicy();
    }

    private static GovernancePolicy toPolicy(GovernanceRuntimeProperties.Policy properties) {
        GovernanceRuntimeProperties.Policy safeProperties =
                properties == null ? new GovernanceRuntimeProperties.Policy() : properties;
        GovernancePolicy.Builder builder = GovernancePolicy.builder()
                .deadline(safeProperties.getDeadline())
                .maxRequestsPerWindow(safeProperties.getMaxRequestsPerWindow())
                .rateLimitWindow(safeProperties.getRateLimitWindow())
                .maxConcurrency(safeProperties.getMaxConcurrency())
                .degraded(safeProperties.isDegraded());
        GovernanceRuntimeProperties.CircuitBreaker circuitBreaker = safeProperties.getCircuitBreaker();
        if (circuitBreaker != null && circuitBreaker.isEnabled()) {
            builder.minimumRequests(circuitBreaker.getMinimumCalls())
                    .failureRateThreshold(circuitBreaker.getFailureRateThreshold())
                    .slowCallThreshold(circuitBreaker.getSlowCallRateThreshold())
                    .slowCallDuration(circuitBreaker.getSlowCallThreshold())
                    .openStateDuration(circuitBreaker.getOpenStateDuration())
                    .halfOpenMaxCalls(circuitBreaker.getHalfOpenProbeCalls())
                    .slidingWindowSize(circuitBreaker.getSlidingWindowSize())
                    .slidingWindowDuration(circuitBreaker.getWindow())
                    .consecutiveFailureThreshold(circuitBreaker.getConsecutiveFailureThreshold());
        }
        return builder.build();
    }

    private static GovernanceResource toResource(String id, GovernanceRuntimeProperties.ResourcePolicy properties) {
        GovernanceRuntimeProperties.ResourcePolicy safeProperties =
                properties == null ? new GovernanceRuntimeProperties.ResourcePolicy() : properties;
        String name = hasText(safeProperties.getName()) ? safeProperties.getName() : id;
        String provider = hasText(safeProperties.getProvider()) ? safeProperties.getProvider() : "nexary";
        String operation = hasText(safeProperties.getOperation()) ? safeProperties.getOperation() : "default";
        return new GovernanceResource(safeProperties.getKind(), name, provider, operation);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
