package org.nexary.governance.sentinel;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.nexary.governance.runtime.GovernancePolicy;

class SentinelRuleMapperTest {
    private final SentinelRuleMapper mapper = new SentinelRuleMapper();

    @Test
    void mapsRateLimitAndConcurrencyToFlowRules() {
        GovernancePolicy policy = GovernancePolicy.builder()
                .maxRequestsPerWindow(30)
                .rateLimitWindow(Duration.ofSeconds(10))
                .maxConcurrency(4)
                .build();

        List<FlowRule> rules = mapper.flowRules("http:orders:nexary:get", policy);

        assertThat(rules).hasSize(2);
        assertThat(rules)
                .anySatisfy(rule -> {
                    assertThat(rule.getGrade()).isEqualTo(RuleConstant.FLOW_GRADE_QPS);
                    assertThat(rule.getCount()).isEqualTo(3.0d);
                })
                .anySatisfy(rule -> {
                    assertThat(rule.getGrade()).isEqualTo(RuleConstant.FLOW_GRADE_THREAD);
                    assertThat(rule.getCount()).isEqualTo(4.0d);
                });
    }

    @Test
    void mapsSlowAndFailurePoliciesToDegradeRules() {
        GovernancePolicy policy = GovernancePolicy.builder()
                .minimumRequests(5)
                .failureRateThreshold(50.0d)
                .slowCallThreshold(75.0d)
                .slowCallDuration(Duration.ofMillis(200))
                .openStateDuration(Duration.ofSeconds(3))
                .slidingWindowDuration(Duration.ofSeconds(10))
                .consecutiveFailureThreshold(7)
                .build();

        List<DegradeRule> rules = mapper.degradeRules("http:orders:nexary:get", policy);

        assertThat(rules).hasSize(3);
        assertThat(rules)
                .anySatisfy(rule -> {
                    assertThat(rule.getGrade()).isEqualTo(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO);
                    assertThat(rule.getCount()).isEqualTo(0.5d);
                })
                .anySatisfy(rule -> {
                    assertThat(rule.getGrade()).isEqualTo(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT);
                    assertThat(rule.getCount()).isEqualTo(7.0d);
                })
                .anySatisfy(rule -> {
                    assertThat(rule.getGrade()).isEqualTo(RuleConstant.DEGRADE_GRADE_RT);
                    assertThat(rule.getCount()).isEqualTo(200.0d);
                    assertThat(rule.getSlowRatioThreshold()).isEqualTo(0.75d);
                });
        assertThat(rules).allSatisfy(rule -> {
            assertThat(rule.getMinRequestAmount()).isEqualTo(5);
            assertThat(rule.getTimeWindow()).isEqualTo(3);
            assertThat(rule.getStatIntervalMs()).isEqualTo(10_000);
        });
    }
}
