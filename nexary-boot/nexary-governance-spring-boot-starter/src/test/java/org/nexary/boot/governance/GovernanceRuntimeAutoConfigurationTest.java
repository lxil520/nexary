package org.nexary.boot.governance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.runtime.GovernancePolicy;
import org.nexary.governance.runtime.GovernancePolicyRegistry;
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

    @Test
    void bindsTypedPolicyConfigurationIntoRegistry() {
        contextRunner
                .withPropertyValues(
                        "nexary.governance.default-policy.deadline=2s",
                        "nexary.governance.default-policy.max-concurrency=8",
                        "nexary.governance.resources.checkout.kind=http",
                        "nexary.governance.resources.checkout.name=checkout-api",
                        "nexary.governance.resources.checkout.operation=submit",
                        "nexary.governance.resources.checkout.max-requests-per-window=2",
                        "nexary.governance.resources.checkout.rate-limit-window=30s",
                        "nexary.governance.resources.checkout.max-concurrency=1",
                        "nexary.governance.resources.checkout.priorities.low.degraded=true")
                .run(context -> {
                    GovernancePolicyRegistry registry = context.getBean(GovernancePolicyRegistry.class);
                    GovernanceResource resource = GovernanceResource.http("checkout-api", "submit");

                    GovernancePolicy normalPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(resource)
                            .build());
                    GovernancePolicy lowPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(resource)
                            .trafficTag(TrafficTag.builder().priority(TrafficTag.Priority.LOW).build())
                            .build());
                    GovernancePolicy defaultPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(GovernanceResource.http("other-api", "submit"))
                            .build());

                    assertThat(normalPolicy.maxRequestsPerWindow()).isEqualTo(2);
                    assertThat(normalPolicy.rateLimitWindow()).hasSeconds(30);
                    assertThat(normalPolicy.maxConcurrency()).isEqualTo(1);
                    assertThat(lowPolicy.degraded()).isTrue();
                    assertThat(defaultPolicy.deadline()).hasValueSatisfying(deadline ->
                            assertThat(deadline).hasSeconds(2));
                    assertThat(defaultPolicy.maxConcurrency()).isEqualTo(8);
                });
    }

    @Test
    void keepsRuntimeScopedDefaultPolicyConfiguration() {
        contextRunner
                .withPropertyValues("nexary.governance.runtime.default-policy.max-concurrency=3")
                .run(context -> {
                    GovernancePolicyRegistry registry = context.getBean(GovernancePolicyRegistry.class);
                    GovernancePolicy defaultPolicy = registry.policyFor(GovernanceContext.builder()
                            .resource(GovernanceResource.http("legacy-api", "get"))
                            .build());

                    assertThat(defaultPolicy.maxConcurrency()).isEqualTo(3);
                });
    }
}
