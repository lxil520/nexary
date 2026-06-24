package org.nexary.boot.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nexary.console.server.ConsoleApiController;
import org.nexary.console.server.ConsoleDiagnosticsService;
import org.nexary.console.server.ConsolePageController;
import org.nexary.governance.runtime.GovernanceDiagnostics;
import org.nexary.governance.runtime.GovernanceResourceDescriptor;
import org.nexary.governance.runtime.GovernanceRuntimeEvent;
import org.nexary.governance.runtime.GovernanceRuntimeSnapshot;
import org.nexary.governance.runtime.GovernanceRuntimeSummary;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class ConsoleAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConsoleAutoConfiguration.class));

    @Test
    void consoleApiIsDisabledByDefault() {
        contextRunner
                .withBean(GovernanceDiagnostics.class, EmptyDiagnostics::new)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ConsoleDiagnosticsService.class)
                        .doesNotHaveBean(ConsoleApiController.class)
                        .doesNotHaveBean(ConsolePageController.class));
    }

    @Test
    void consoleApiIsEnabledExplicitly() {
        contextRunner
                .withBean(GovernanceDiagnostics.class, EmptyDiagnostics::new)
                .withPropertyValues("nexary.console.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(ConsoleDiagnosticsService.class)
                        .hasSingleBean(ConsoleApiController.class)
                        .hasSingleBean(ConsolePageController.class));
    }

    @Test
    void consoleApiDoesNotStartWithoutGovernanceDiagnostics() {
        contextRunner
                .withPropertyValues("nexary.console.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ConsoleDiagnosticsService.class)
                        .doesNotHaveBean(ConsoleApiController.class)
                        .doesNotHaveBean(ConsolePageController.class));
    }

    private static final class EmptyDiagnostics implements GovernanceDiagnostics {
        @Override
        public List<GovernanceResourceDescriptor> resources() {
            return Collections.emptyList();
        }

        @Override
        public List<GovernanceRuntimeSnapshot> snapshots() {
            return Collections.emptyList();
        }

        @Override
        public List<GovernanceRuntimeEvent> recentEvents() {
            return Collections.emptyList();
        }

        @Override
        public GovernanceRuntimeSummary summary() {
            return new GovernanceRuntimeSummary(0, 0, 0, 0L, 0L, 0L, 0L, 0L, 0L, null);
        }
    }
}
