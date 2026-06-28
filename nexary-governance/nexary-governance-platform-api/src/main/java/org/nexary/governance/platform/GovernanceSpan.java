package org.nexary.governance.platform;

import java.util.List;
import java.util.Objects;

/**
 * One sanitized span in a platform request flow.
 *
 * @param spanId sanitized span id
 * @param parentSpanId optional sanitized parent span id
 * @param serviceKey source service key
 * @param resourceKey resource, endpoint, route, or middleware key
 * @param component low-cardinality component name
 * @param operation low-cardinality operation name
 * @param startOffsetMs offset from flow start in milliseconds
 * @param durationMs span duration in milliseconds
 * @param status fixed status bucket
 * @param errorType fixed error bucket, or NONE
 * @param evidenceRefs external evidence references
 */
public record GovernanceSpan(
        String spanId,
        String parentSpanId,
        String serviceKey,
        String resourceKey,
        String component,
        String operation,
        long startOffsetMs,
        long durationMs,
        String status,
        String errorType,
        List<GovernanceEvidenceRef> evidenceRefs) {

    /** Creates a sanitized span. */
    public GovernanceSpan {
        spanId = GovernancePlatformValidators.token(spanId, "spanId");
        parentSpanId = parentSpanId == null || parentSpanId.isBlank()
                ? ""
                : GovernancePlatformValidators.token(parentSpanId, "parentSpanId");
        serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        resourceKey = GovernancePlatformValidators.token(resourceKey, "resourceKey");
        component = GovernancePlatformValidators.token(component == null ? "unknown" : component, "component");
        operation = GovernancePlatformValidators.label(operation == null ? resourceKey : operation, "operation");
        status = GovernancePlatformValidators.token(status == null ? "OK" : status, "status");
        errorType = GovernancePlatformValidators.token(errorType == null ? "NONE" : errorType, "errorType");
        startOffsetMs = Math.max(0, startOffsetMs);
        durationMs = Math.max(0, durationMs);
        evidenceRefs = List.copyOf(Objects.requireNonNullElse(evidenceRefs, List.of()));
    }
}
