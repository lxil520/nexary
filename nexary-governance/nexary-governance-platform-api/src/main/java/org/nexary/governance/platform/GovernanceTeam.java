package org.nexary.governance.platform;

import java.util.Map;

/** Team asset that owns services or clusters. */
public final class GovernanceTeam extends GovernanceAsset {
    /** Creates a team asset. */
    public GovernanceTeam(String key, String name, Map<String, String> attributes) {
        super(GovernanceAssetKind.TEAM, key, name, attributes);
    }
}
