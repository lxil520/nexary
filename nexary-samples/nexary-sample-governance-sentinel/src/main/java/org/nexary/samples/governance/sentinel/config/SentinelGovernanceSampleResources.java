package org.nexary.samples.governance.sentinel.config;

import org.nexary.core.governance.GovernanceResource;
import org.springframework.context.annotation.Configuration;

/** Stable resources used by the Sentinel governance sample. */
@Configuration
public class SentinelGovernanceSampleResources {
    public static final GovernanceResource RATE_RESOURCE = GovernanceResource.http("sentinel-api", "rate");
    public static final GovernanceResource BULKHEAD_RESOURCE = GovernanceResource.http("sentinel-api", "bulkhead");
    public static final GovernanceResource SLOW_RESOURCE = GovernanceResource.downstream("sentinel-slow-service", "load");
    public static final GovernanceResource FAILURE_RESOURCE = GovernanceResource.downstream("sentinel-failure-service", "load");
    public static final GovernanceResource DEGRADED_RESOURCE = GovernanceResource.downstream("sentinel-degraded-service", "load");
    public static final GovernanceResource PRIORITY_RESOURCE = GovernanceResource.downstream("priority-shared-service", "load");
}
