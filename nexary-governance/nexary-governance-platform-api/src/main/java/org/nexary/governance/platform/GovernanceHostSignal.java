package org.nexary.governance.platform;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Sanitized host or instance waterline used by the platform matrix.
 *
 * @param hostKey host or instance alias
 * @param serviceKey owning service key
 * @param clusterKey cluster key
 * @param zoneKey zone key
 * @param state fixed health state
 * @param cpuPercent CPU usage percent
 * @param memoryPercent memory usage percent
 * @param swapPercent swap usage percent
 * @param diskIoPercent disk IO pressure percent
 * @param networkJitterMs network jitter in milliseconds
 * @param packetLossPercent packet loss percent
 * @param connectionCount open connection count
 * @param jvmThreadCount JVM thread count
 * @param gcPauseMs recent GC pause in milliseconds
 * @param lastError fixed latest error bucket
 * @param lastSeenAt last seen time
 * @param attributes bounded low-cardinality attributes
 */
public record GovernanceHostSignal(
        String hostKey,
        String serviceKey,
        String clusterKey,
        String zoneKey,
        String state,
        double cpuPercent,
        double memoryPercent,
        double swapPercent,
        double diskIoPercent,
        double networkJitterMs,
        double packetLossPercent,
        long connectionCount,
        long jvmThreadCount,
        long gcPauseMs,
        String lastError,
        Instant lastSeenAt,
        Map<String, String> attributes) {

    /** Creates a sanitized host signal. */
    public GovernanceHostSignal {
        hostKey = GovernancePlatformValidators.token(hostKey, "hostKey");
        serviceKey = GovernancePlatformValidators.token(serviceKey, "serviceKey");
        clusterKey = GovernancePlatformValidators.token(clusterKey, "clusterKey");
        zoneKey = GovernancePlatformValidators.token(zoneKey, "zoneKey");
        state = GovernancePlatformValidators.token(state == null ? "HEALTHY" : state, "state");
        cpuPercent = boundedPercent(cpuPercent);
        memoryPercent = boundedPercent(memoryPercent);
        swapPercent = boundedPercent(swapPercent);
        diskIoPercent = boundedPercent(diskIoPercent);
        networkJitterMs = Math.max(0.0, networkJitterMs);
        packetLossPercent = boundedPercent(packetLossPercent);
        connectionCount = Math.max(0, connectionCount);
        jvmThreadCount = Math.max(0, jvmThreadCount);
        gcPauseMs = Math.max(0, gcPauseMs);
        lastError = GovernancePlatformValidators.token(lastError == null ? "NONE" : lastError, "lastError");
        lastSeenAt = lastSeenAt == null ? Instant.now() : lastSeenAt;
        attributes = GovernancePlatformValidators.attributes(Objects.requireNonNullElse(attributes, Map.of()));
    }

    private static double boundedPercent(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }
}
