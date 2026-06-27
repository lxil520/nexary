package org.nexary.governance.platform;

/** Low-cardinality impacted scope for an incident candidate. */
public final class ImpactScope {
    private final String serviceKey;
    private final String clusterKey;
    private final String zoneKey;

    /** Creates an impacted scope. */
    public ImpactScope(String serviceKey, String clusterKey, String zoneKey) {
        this.serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        this.clusterKey = GovernancePlatformValidators.token(clusterKey, "clusterKey");
        this.zoneKey = GovernancePlatformValidators.token(zoneKey, "zoneKey");
    }

    /** Returns the impacted service key. */
    public String serviceKey() { return serviceKey; }
    /** Returns the impacted cluster key. */
    public String clusterKey() { return clusterKey; }
    /** Returns the impacted zone key. */
    public String zoneKey() { return zoneKey; }
}
