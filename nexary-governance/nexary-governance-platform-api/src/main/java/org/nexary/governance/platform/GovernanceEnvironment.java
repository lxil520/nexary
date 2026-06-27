package org.nexary.governance.platform;

import java.util.Map;

/** Environment asset such as prod, staging, or local demo. */
public final class GovernanceEnvironment extends GovernanceAsset {
    /** Creates an environment asset. */
    public GovernanceEnvironment(String key, String name, Map<String, String> attributes) {
        super(GovernanceAssetKind.ENVIRONMENT, key, name, attributes);
    }
}
