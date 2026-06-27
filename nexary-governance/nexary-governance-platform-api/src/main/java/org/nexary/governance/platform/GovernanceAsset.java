package org.nexary.governance.platform;

import java.util.Map;
import java.util.Objects;

/** Immutable low-cardinality platform asset used by topology and service views. */
public class GovernanceAsset {
    private final GovernanceAssetKind kind;
    private final String key;
    private final String name;
    private final Map<String, String> attributes;

    /** Creates a platform asset. */
    public GovernanceAsset(GovernanceAssetKind kind, String key, String name, Map<String, String> attributes) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.key = GovernancePlatformValidators.token(key, "key");
        this.name = GovernancePlatformValidators.label(name, "name");
        this.attributes = GovernancePlatformValidators.attributes(attributes);
    }

    /** Returns the fixed asset kind. */
    public GovernanceAssetKind kind() {
        return kind;
    }

    /** Returns the stable low-cardinality asset key. */
    public String key() {
        return key;
    }

    /** Returns the human-facing asset name. */
    public String name() {
        return name;
    }

    /** Returns bounded low-cardinality attributes. */
    public Map<String, String> attributes() {
        return attributes;
    }
}
