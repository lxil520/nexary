package org.nexary.core.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GovernanceResourceTest {
    @Test
    void normalizesEmptyValues() {
        GovernanceResource resource = new GovernanceResource(null, "", "");

        assertThat(resource.kind()).isEqualTo(GovernanceResource.ResourceKind.CUSTOM);
        assertThat(resource.name()).isEqualTo("unknown");
        assertThat(resource.provider()).isEqualTo("unknown");
    }

    @Test
    void exposesOnlyBoundedMetricTags() {
        GovernanceResource resource = GovernanceResource.messaging("order-events", "kafka");

        assertThat(resource.tags()).containsOnly(
                Map.entry("resource_kind", "messaging"),
                Map.entry("resource", "order-events"),
                Map.entry("provider", "kafka"),
                Map.entry("operation", "default"));
    }

    @Test
    void exposesCacheResourceKindAndOperation() {
        GovernanceResource resource = GovernanceResource.cache("session-cache", "redis", "get");

        assertThat(resource.tags()).containsOnly(
                Map.entry("resource_kind", "cache"),
                Map.entry("resource", "session-cache"),
                Map.entry("provider", "redis"),
                Map.entry("operation", "get"));
    }
}
