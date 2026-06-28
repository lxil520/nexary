package org.nexary.governance.platform;

import java.util.Objects;

/**
 * Low-cardinality pointer to an external evidence system.
 *
 * @param type external evidence system type
 * @param refKey sanitized reference key
 * @param label operator-facing label
 * @param href optional read-only URL or route placeholder
 */
public record GovernanceEvidenceRef(
        GovernanceEvidenceRefType type,
        String refKey,
        String label,
        String href) {

    /** Creates a sanitized evidence reference. */
    public GovernanceEvidenceRef {
        type = Objects.requireNonNull(type, "type");
        refKey = GovernancePlatformValidators.token(refKey, "refKey");
        label = GovernancePlatformValidators.label(label == null ? refKey : label, "label");
        href = href == null || href.isBlank() ? "" : GovernancePlatformValidators.label(href, "href");
    }
}
