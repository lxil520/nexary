package org.nexary.boot.governance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GovernanceRuntimeAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GovernanceRuntimeAutoConfiguration.class));

    @Test
    void createsLocalRuntimeByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(GovernanceRuntime.class));
    }

    @Test
    void canDisableRuntime() {
        contextRunner
                .withPropertyValues("nexary.governance.runtime.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GovernanceRuntime.class));
    }
}
