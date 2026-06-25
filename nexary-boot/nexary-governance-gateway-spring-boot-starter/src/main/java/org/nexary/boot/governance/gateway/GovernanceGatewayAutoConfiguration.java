package org.nexary.boot.governance.gateway;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/** Auto-configuration for Nexary Gateway request cancellation propagation. */
@AutoConfiguration
@EnableConfigurationProperties(GovernanceGatewayProperties.class)
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnProperty(prefix = "nexary.governance.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GovernanceGatewayAutoConfiguration {
    /** Creates the Gateway filter that forwards cancellation headers and notifies downstream cancellation receivers. */
    @Bean
    @ConditionalOnMissingBean
    public GovernanceGatewayCancellationFilter nexaryGovernanceGatewayCancellationFilter(
            GovernanceGatewayProperties properties,
            WebClient.Builder webClientBuilder) {
        return new GovernanceGatewayCancellationFilter(properties, webClientBuilder.build());
    }
}
