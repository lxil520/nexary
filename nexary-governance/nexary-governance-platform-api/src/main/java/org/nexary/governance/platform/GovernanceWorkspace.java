package org.nexary.governance.platform;

import java.util.Map;

/** Workspace asset grouping one platform installation or product line. */
public final class GovernanceWorkspace extends GovernanceAsset {
    /** Creates a workspace asset. */
    public GovernanceWorkspace(String key, String name, Map<String, String> attributes) {
        super(GovernanceAssetKind.WORKSPACE, key, name, attributes);
    }
}
