package org.nexary.governance.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.nexary.governance.runtime.GovernancePolicy;

/** Maps Nexary governance policies to Sentinel rules. */
public final class SentinelRuleMapper {
    /** Returns Sentinel flow rules for the given resource and policy. */
    public List<FlowRule> flowRules(String resourceKey, GovernancePolicy policy) {
        GovernancePolicy safePolicy = policy == null ? GovernancePolicy.allowAll() : policy;
        List<FlowRule> rules = new ArrayList<>();
        if (safePolicy.maxRequestsPerWindow() != Integer.MAX_VALUE) {
            double qps = qps(safePolicy.maxRequestsPerWindow(), safePolicy.rateLimitWindow());
            rules.add(new FlowRule(resourceKey)
                    .setGrade(RuleConstant.FLOW_GRADE_QPS)
                    .setCount(Math.max(1.0d, qps)));
        }
        if (safePolicy.maxConcurrency() != Integer.MAX_VALUE) {
            rules.add(new FlowRule(resourceKey)
                    .setGrade(RuleConstant.FLOW_GRADE_THREAD)
                    .setCount(safePolicy.maxConcurrency()));
        }
        return rules;
    }

    /** Returns Sentinel degrade rules for the given resource and policy. */
    public List<DegradeRule> degradeRules(String resourceKey, GovernancePolicy policy) {
        GovernancePolicy safePolicy = policy == null ? GovernancePolicy.allowAll() : policy;
        List<DegradeRule> rules = new ArrayList<>();
        if (safePolicy.failureRateThreshold() != Double.POSITIVE_INFINITY) {
            rules.add(baseDegradeRule(resourceKey, safePolicy)
                    .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
                    .setCount(safePolicy.failureRateThreshold() / 100.0d));
        }
        if (safePolicy.consecutiveFailureThreshold() != Integer.MAX_VALUE) {
            rules.add(baseDegradeRule(resourceKey, safePolicy)
                    .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT)
                    .setCount(safePolicy.consecutiveFailureThreshold()));
        }
        if (safePolicy.slowCallDuration().isPresent()
                && safePolicy.slowCallThreshold() != Double.POSITIVE_INFINITY) {
            rules.add(baseDegradeRule(resourceKey, safePolicy)
                    .setGrade(RuleConstant.DEGRADE_GRADE_RT)
                    .setCount(Math.max(1L, safePolicy.slowCallDuration().get().toMillis()))
                    .setSlowRatioThreshold(safePolicy.slowCallThreshold() / 100.0d));
        }
        return rules;
    }

    private static DegradeRule baseDegradeRule(String resourceKey, GovernancePolicy policy) {
        return new DegradeRule(resourceKey)
                .setMinRequestAmount(policy.minimumRequests())
                .setStatIntervalMs(safeMillis(policy.slidingWindowDuration()))
                .setTimeWindow(safeSeconds(policy.openStateDuration()));
    }

    private static double qps(int maxRequests, Duration window) {
        long millis = window == null ? 1000L : Math.max(1L, window.toMillis());
        return maxRequests * 1000.0d / millis;
    }

    private static int safeMillis(Duration duration) {
        long millis = duration == null ? 1000L : Math.max(1L, duration.toMillis());
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private static int safeSeconds(Duration duration) {
        long seconds = duration == null ? 1L : Math.max(1L, duration.getSeconds());
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }
}
