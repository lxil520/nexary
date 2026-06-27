package org.nexary.governance.platform;

import java.util.Map;

/** Zone asset such as a cloud region, availability zone, or phone-room area. */
public final class GovernanceZone extends GovernanceAsset {
    /** Creates a zone asset. */
    public GovernanceZone(String key, String name, Map<String, String> attributes) {
        super(GovernanceAssetKind.ZONE, key, name, attributes);
    }
}
