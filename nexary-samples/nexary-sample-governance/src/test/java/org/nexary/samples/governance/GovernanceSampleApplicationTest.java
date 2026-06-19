package org.nexary.samples.governance;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.samples.governance.api.GovernanceSampleController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = org.nexary.samples.governance.app.GovernanceSampleApplication.class)
@Import(GovernanceSampleApplicationTest.MetricsTestConfiguration.class)
class GovernanceSampleApplicationTest {
    @Autowired
    private GovernanceRuntime governanceRuntime;

    @Autowired
    private GovernanceSampleController controller;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void startsWithGovernanceRuntime() {
        assertThat(governanceRuntime).isNotNull();
    }

    @Test
    void recordsGovernanceObservationMetersWithBoundedTags() throws Exception {
        controller.profile("u-1");
        controller.profile("u-2");
        controller.profile("u-3");

        assertThat(meterRegistry.find("nexary.observation.events.total")
                .tag("category", "governance")
                .tag("operation", "governance.rate_limited")
                .tag("resource_kind", "http")
                .tag("governance_action", "rate_limited")
                .tag("traffic_channel", "online")
                .tag("traffic_priority", "normal")
                .counter()).isNotNull();
        assertThat(meterRegistry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().equals("resource"))
                        .noneMatch(tag -> tag.getKey().equals("tenant"))
                        .noneMatch(tag -> tag.getKey().equals("biz_key"))
                        .noneMatch(tag -> tag.getKey().equals("user_id")));
    }

    @TestConfiguration
    static class MetricsTestConfiguration {
        @Bean
        MeterRegistry governanceSampleMeterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
