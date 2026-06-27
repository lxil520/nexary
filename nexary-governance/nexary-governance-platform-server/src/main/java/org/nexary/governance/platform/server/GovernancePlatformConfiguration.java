package org.nexary.governance.platform.server;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring configuration for the read-only governance platform server. */
@Configuration(proxyBeanMethods = false)
public class GovernancePlatformConfiguration {

    /** Creates an in-memory repository when no persistent repository is provided. */
    @Bean
    @ConditionalOnMissingBean
    public GovernancePlatformRepository governancePlatformRepository() {
        return new InMemoryGovernancePlatformRepository();
    }

    /** Creates the platform service. */
    @Bean
    @ConditionalOnMissingBean
    public GovernancePlatformService governancePlatformService(GovernancePlatformRepository repository) {
        return new GovernancePlatformService(repository);
    }

    /** Creates the platform HTTP controller. */
    @Bean
    @ConditionalOnMissingBean
    public GovernancePlatformController governancePlatformController(GovernancePlatformService service) {
        return new GovernancePlatformController(service);
    }
}
