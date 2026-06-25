package org.nexary.samples.governance.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.boot.governance.gateway.GovernanceGatewayCancellationFilter;
import org.nexary.samples.governance.gateway.app.GovernanceGatewaySampleApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;

@SpringBootTest(
        classes = GovernanceGatewaySampleApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GovernanceGatewaySampleApplicationTest {
    @Autowired
    private GovernanceGatewayCancellationFilter cancellationFilter;

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void startsWithGatewayCancellationFilterAndRoute() {
        assertThat(cancellationFilter).isNotNull();
        assertThat(routeLocator.getRoutes().collectList().block())
                .anySatisfy(route -> assertThat(route.getId()).isEqualTo("governance-downstream"));
    }
}
