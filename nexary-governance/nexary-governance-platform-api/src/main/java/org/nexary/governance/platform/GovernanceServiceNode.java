package org.nexary.governance.platform;

import java.util.Map;

/** Service-level topology node with current aggregate platform signal counts. */
public final class GovernanceServiceNode {
    private final String serviceKey;
    private final String name;
    private final String teamKey;
    private final String environmentKey;
    private final String clusterKey;
    private final String zoneKey;
    private final long warningCount;
    private final long criticalCount;
    private final Map<String, String> attributes;

    /** Creates a service topology node. */
    public GovernanceServiceNode(
            String serviceKey,
            String name,
            String teamKey,
            String environmentKey,
            String clusterKey,
            String zoneKey,
            long warningCount,
            long criticalCount,
            Map<String, String> attributes) {
        this.serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        this.name = GovernancePlatformValidators.label(name, "name");
        this.teamKey = GovernancePlatformValidators.token(teamKey == null ? "unknown" : teamKey, "teamKey");
        this.environmentKey = GovernancePlatformValidators.token(environmentKey == null ? "unknown" : environmentKey, "environmentKey");
        this.clusterKey = GovernancePlatformValidators.token(clusterKey == null ? "unknown" : clusterKey, "clusterKey");
        this.zoneKey = GovernancePlatformValidators.token(zoneKey == null ? "unknown" : zoneKey, "zoneKey");
        this.warningCount = Math.max(0L, warningCount);
        this.criticalCount = Math.max(0L, criticalCount);
        this.attributes = GovernancePlatformValidators.attributes(attributes);
    }

    /** Returns the service key. */
    public String serviceKey() { return serviceKey; }
    /** Returns the service display name. */
    public String name() { return name; }
    /** Returns the owning team key. */
    public String teamKey() { return teamKey; }
    /** Returns the environment key. */
    public String environmentKey() { return environmentKey; }
    /** Returns the cluster key. */
    public String clusterKey() { return clusterKey; }
    /** Returns the zone key. */
    public String zoneKey() { return zoneKey; }
    /** Returns retained warning signal count. */
    public long warningCount() { return warningCount; }
    /** Returns retained critical signal count. */
    public long criticalCount() { return criticalCount; }
    /** Returns bounded low-cardinality attributes. */
    public Map<String, String> attributes() { return attributes; }
}
