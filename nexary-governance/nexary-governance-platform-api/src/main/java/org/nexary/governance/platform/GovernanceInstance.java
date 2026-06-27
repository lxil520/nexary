package org.nexary.governance.platform;

import java.util.Map;

/** Instance asset using a stable low-cardinality alias instead of raw host or port. */
public final class GovernanceInstance extends GovernanceAsset {
    /** Creates an instance asset. */
    public GovernanceInstance(String key, String name, Map<String, String> attributes) {
        super(GovernanceAssetKind.INSTANCE, key, name, attributes);
    }
}
