package org.nexary.samples.governance.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.governance.runtime.GovernanceEngine;
import org.nexary.governance.runtime.GovernanceRuntime;
import org.nexary.governance.sentinel.SentinelGovernanceRuntime;
import org.nexary.samples.governance.sentinel.app.SentinelGovernanceSampleApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = SentinelGovernanceSampleApplication.class)
class SentinelGovernanceSampleApplicationTest {
    @Autowired
    private GovernanceRuntime governanceRuntime;

    @Test
    void startsWithSentinelRuntime() {
        assertThat(governanceRuntime).isInstanceOf(SentinelGovernanceRuntime.class);
        assertThat(governanceRuntime.resources())
                .isNotEmpty()
                .allMatch(resource -> resource.engine() == GovernanceEngine.SENTINEL);
    }
}
