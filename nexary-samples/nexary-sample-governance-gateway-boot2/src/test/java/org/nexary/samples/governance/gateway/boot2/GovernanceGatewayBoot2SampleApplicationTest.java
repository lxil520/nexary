package org.nexary.samples.governance.gateway.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.boot.governance.gateway.GovernanceGatewayCancellationFilter;
import org.nexary.samples.governance.gateway.boot2.app.GovernanceGatewayBoot2SampleApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest(
        classes = GovernanceGatewayBoot2SampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GovernanceGatewayBoot2SampleApplicationTest {
    @Autowired
    private GovernanceGatewayCancellationFilter cancellationFilter;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void startsWithBoot2GatewayCancellationFilterAndRoute() {
        assertThat(cancellationFilter).isNotNull();
        assertThat(routeLocator.getRoutes().collectList().block())
                .anySatisfy(route -> assertThat(route.getId()).isEqualTo("governance-downstream"));
    }
}
