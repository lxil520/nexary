package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/** Local mapping between a Nexary service key and an external tool object. */
public final class GovernanceServiceMapping {
    private final String mappingKey;
    private final String serviceKey;
    private final String connectorKey;
    private final GovernanceConnectorKind sourceKind;
    private final String externalKey;
    private final String resourceKind;
    private final double confidence;
    private final Map<String, String> attributes;
    private final Instant updatedAt;

    /** Creates a service mapping. */
    public GovernanceServiceMapping(
            String mappingKey,
            String serviceKey,
            String connectorKey,
            GovernanceConnectorKind sourceKind,
            String externalKey,
            String resourceKind,
            double confidence,
            Map<String, String> attributes,
            Instant updatedAt) {
        this.mappingKey = GovernancePlatformValidators.token(mappingKey, "mappingKey");
        this.serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        this.connectorKey = GovernancePlatformValidators.token(connectorKey, "connectorKey");
        this.sourceKind = sourceKind == null ? GovernanceConnectorKind.NEXARY_SDK : sourceKind;
        this.externalKey = GovernancePlatformValidators.label(externalKey, "externalKey");
        this.resourceKind = GovernancePlatformValidators.token(resourceKind == null ? "service" : resourceKind, "resourceKind");
        this.confidence = Math.max(0, Math.min(1, confidence));
        this.attributes = Collections.unmodifiableMap(GovernancePlatformValidators.attributes(attributes));
        this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    /** Returns the stable mapping key. */
    public String mappingKey() { return mappingKey; }
    /** Returns the Nexary service key. */
    public String serviceKey() { return serviceKey; }
    /** Returns the local connector key. */
    public String connectorKey() { return connectorKey; }
    /** Returns the external source kind. */
    public GovernanceConnectorKind sourceKind() { return sourceKind; }
    /** Returns the external object key. */
    public String externalKey() { return externalKey; }
    /** Returns the mapped external resource kind. */
    public String resourceKind() { return resourceKind; }
    /** Returns mapping confidence between 0 and 1. */
    public double confidence() { return confidence; }
    /** Returns public mapping attributes. */
    public Map<String, String> attributes() { return attributes; }
    /** Returns last update time. */
    public Instant updatedAt() { return updatedAt; }
}
