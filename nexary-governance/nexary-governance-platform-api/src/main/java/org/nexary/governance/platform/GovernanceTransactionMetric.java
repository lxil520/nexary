package org.nexary.governance.platform;

import java.time.Instant;

/**
 * CAT-style endpoint transaction metric summarized for the platform.
 *
 * @param serviceKey service key
 * @param endpointKey endpoint or resource key
 * @param zoneKey zone key
 * @param windowStart metric window start
 * @param windowEnd metric window end
 * @param total total calls
 * @param failure failed calls
 * @param failureRate failed calls divided by total calls
 * @param tps transactions per second
 * @param qps queries per second
 * @param minMs minimum latency in milliseconds
 * @param maxMs maximum latency in milliseconds
 * @param avgMs average latency in milliseconds
 * @param p95Ms p95 latency in milliseconds
 * @param p99Ms p99 latency in milliseconds
 * @param sampleTraceKey sanitized sample trace key
 */
public record GovernanceTransactionMetric(
        String serviceKey,
        String endpointKey,
        String zoneKey,
        Instant windowStart,
        Instant windowEnd,
        long total,
        long failure,
        double failureRate,
        double tps,
        double qps,
        long minMs,
        long maxMs,
        long avgMs,
        long p95Ms,
        long p99Ms,
        String sampleTraceKey) {

    /** Creates a sanitized transaction metric. */
    public GovernanceTransactionMetric {
        serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        endpointKey = GovernancePlatformValidators.token(endpointKey, "endpointKey");
        zoneKey = GovernancePlatformValidators.token(zoneKey, "zoneKey");
        windowStart = windowStart == null ? Instant.EPOCH : windowStart;
        windowEnd = windowEnd == null ? windowStart : windowEnd;
        total = Math.max(0, total);
        failure = Math.max(0, failure);
        failureRate = Math.max(0.0, failureRate);
        tps = Math.max(0.0, tps);
        qps = Math.max(0.0, qps);
        minMs = Math.max(0, minMs);
        maxMs = Math.max(0, maxMs);
        avgMs = Math.max(0, avgMs);
        p95Ms = Math.max(0, p95Ms);
        p99Ms = Math.max(0, p99Ms);
        sampleTraceKey = sampleTraceKey == null || sampleTraceKey.isBlank()
                ? ""
                : GovernancePlatformValidators.token(sampleTraceKey, "sampleTraceKey");
    }
}
