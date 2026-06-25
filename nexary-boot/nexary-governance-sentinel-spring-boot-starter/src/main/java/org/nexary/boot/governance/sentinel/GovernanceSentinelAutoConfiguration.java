package org.nexary.boot.governance.sentinel;

import org.nexary.boot.governance.GovernanceRuntimeAutoConfiguration;
import org.nexary.boot.governance.GovernanceRuntimeProperties;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.governance.runtime.GovernancePolicyRegistry;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.sentinel.SentinelGovernanceRuntime;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for the Sentinel-backed Nexary governance runtime. */
@AutoConfiguration(after = GovernanceRuntimeAutoConfiguration.class)
@EnableConfigurationProperties(GovernanceRuntimeProperties.class)
@ConditionalOnClass(SentinelGovernanceRuntime.class)
@ConditionalOnProperty(prefix = "nexary.governance", name = "provider", havingValue = "sentinel")
public class GovernanceSentinelAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "nexary.governance.sentinel", name = "enabled", havingValue = "true", matchIfMissing = true)
    public GovernanceRuntime nexarySentinelGovernanceRuntime(
            GovernancePolicyRegistry policyRegistry,
            java.util.Optional<NexaryObservationPublisher> observationPublisher,
            GovernanceRuntimeProperties properties) {
        configureTransport(properties.getSentinel());
        return new SentinelGovernanceRuntime(
                policyRegistry,
                observationPublisher.orElse(NexaryObservationPublisher.noop()));
    }

    private static void configureTransport(GovernanceRuntimeProperties.Sentinel sentinel) {
        if (sentinel == null || sentinel.getTransport() == null || !sentinel.getTransport().isEnabled()) {
            return;
        }
        String dashboardServer = sentinel.getTransport().getDashboardServer();
        if (dashboardServer != null && !dashboardServer.trim().isEmpty()) {
            System.setProperty("csp.sentinel.dashboard.server", dashboardServer.trim());
        }
    }
}
