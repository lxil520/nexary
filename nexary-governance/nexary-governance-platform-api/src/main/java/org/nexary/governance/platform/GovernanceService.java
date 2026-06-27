package org.nexary.governance.platform;

import java.util.Map;

/** Service asset shown in the governance platform service list and topology. */
public final class GovernanceService extends GovernanceAsset {
    /** Creates a service asset. */
    public GovernanceService(String key, String name, Map<String, String> attributes) {
        super(GovernanceAssetKind.SERVICE, key, name, attributes);
    }
}
