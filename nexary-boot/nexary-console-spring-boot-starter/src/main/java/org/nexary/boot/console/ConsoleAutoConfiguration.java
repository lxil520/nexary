package org.nexary.boot.console;

import org.nexary.console.server.ConsoleApiController;
import org.nexary.console.server.ConsoleDiagnosticsService;
import org.nexary.console.server.ConsolePageController;
import org.nexary.governance.runtime.GovernanceDiagnostics;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/** Auto-configuration for the read-only local Nexary console API. */
@AutoConfiguration(afterName = "org.nexary.boot.governance.GovernanceRuntimeAutoConfiguration")
@ConditionalOnClass({RestController.class, GovernanceDiagnostics.class, ConsoleApiController.class, ConsolePageController.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "nexary.console", name = "enabled", havingValue = "true")
public class ConsoleAutoConfiguration {
    /** Creates the console diagnostics mapper when governance diagnostics are available. */
    @Bean
    @ConditionalOnBean(GovernanceDiagnostics.class)
    @ConditionalOnMissingBean
    public ConsoleDiagnosticsService nexaryConsoleDiagnosticsService(GovernanceDiagnostics diagnostics) {
        return new ConsoleDiagnosticsService(diagnostics);
    }

    /** Creates the read-only console API controller. */
    @Bean
    @ConditionalOnBean(ConsoleDiagnosticsService.class)
    @ConditionalOnMissingBean
    public ConsoleApiController nexaryConsoleApiController(ConsoleDiagnosticsService diagnosticsService) {
        return new ConsoleApiController(diagnosticsService);
    }

    /** Creates the read-only console page controller. */
    @Bean
    @ConditionalOnBean(ConsoleDiagnosticsService.class)
    @ConditionalOnMissingBean
    public ConsolePageController nexaryConsolePageController() {
        return new ConsolePageController();
    }
}
