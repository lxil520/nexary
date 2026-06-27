package org.nexary.governance.platform;

import java.util.Map;

/** Cluster asset grouping service instances in one deployable group. */
public final class GovernanceCluster extends GovernanceAsset {
    /** Creates a cluster asset. */
    public GovernanceCluster(String key, String name, Map<String, String> attributes) {
        super(GovernanceAssetKind.CLUSTER, key, name, attributes);
    }
}
