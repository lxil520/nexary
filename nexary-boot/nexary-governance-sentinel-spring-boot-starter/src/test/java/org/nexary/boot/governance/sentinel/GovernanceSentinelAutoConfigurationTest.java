package org.nexary.boot.governance.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.boot.governance.GovernanceRuntimeAutoConfiguration;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.LocalGovernanceRuntime;
import org.nexary.governance.sentinel.SentinelGovernanceRuntime;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GovernanceSentinelAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    GovernanceRuntimeAutoConfiguration.class,
                    GovernanceSentinelAutoConfiguration.class));

    @Test
    void keepsLocalRuntimeByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GovernanceRuntime.class);
            assertThat(context.getBean(GovernanceRuntime.class)).isInstanceOf(LocalGovernanceRuntime.class);
        });
    }

    @Test
    void createsSentinelRuntimeWhenProviderIsSentinel() {
        contextRunner
                .withPropertyValues("nexary.governance.provider=sentinel")
                .run(context -> {
                    assertThat(context).hasSingleBean(GovernanceRuntime.class);
                    assertThat(context.getBean(GovernanceRuntime.class)).isInstanceOf(SentinelGovernanceRuntime.class);
                });
    }

    @Test
    void canDisableSentinelRuntime() {
        contextRunner
                .withPropertyValues(
                        "nexary.governance.provider=sentinel",
                        "nexary.governance.sentinel.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(GovernanceRuntime.class));
    }

    @Test
    void transportIsOptIn() {
        contextRunner
                .withPropertyValues(
                        "nexary.governance.provider=sentinel",
                        "nexary.governance.sentinel.transport.enabled=true",
                        "nexary.governance.sentinel.transport.dashboard-server=127.0.0.1:8858")
                .run(context -> {
                    assertThat(context).hasSingleBean(SentinelGovernanceRuntime.class);
                    assertThat(System.getProperty("csp.sentinel.dashboard.server")).isEqualTo("127.0.0.1:8858");
                });
    }
}
