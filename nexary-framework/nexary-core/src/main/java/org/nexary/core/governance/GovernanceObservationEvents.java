package org.nexary.core.governance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.core.context.TrafficTag;
import org.nexary.core.fault.FaultSignal;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.core.retry.RetrySignal;

/** Factory methods for governance observation events with bounded tag names. */
public final class GovernanceObservationEvents {
    private GovernanceObservationEvents() {
    }

    /** Creates an event for an operation rejected after its deadline. */
    public static NexaryObservationEvent deadlineExceeded(
            GovernanceResource resource,
            TrafficTag trafficTag,
            Instant startedAt,
            Instant endedAt) {
        Map<String, String> tags = baseTags(resource, trafficTag);
        tags.put("governance_action", "deadline_exceeded");
        tags.put("outcome", "rejected");
        tags.put("failure_category", "timeout");
        return event("governance.deadline.exceeded", startedAt, endedAt, trafficTag,
                new FaultSignal(FaultSignal.FaultType.TIMEOUT, provider(resource), "", endedAt, true), tags);
    }

    /** Creates an event for a retry decision that tells upstream callers to stop retrying. */
    public static NexaryObservationEvent retryStopped(
            GovernanceResource resource,
            TrafficTag trafficTag,
            RetrySignal retrySignal,
            Instant startedAt,
            Instant endedAt) {
        Map<String, String> tags = baseTags(resource, trafficTag);
        tags.put("governance_action", "retry_stopped");
        tags.put("outcome", "stopped");
        tags.put("retry_decision", "stop");
        tags.put("retry_phase", "stopped");
        tags.put("retry_attempt_bucket", attemptBucket(retrySignal == null ? 0 : retrySignal.attempts()));
        return event("governance.retry.stopped", startedAt, endedAt, trafficTag, null, tags);
    }

    /** Creates an event for a rate-limited operation. */
    public static NexaryObservationEvent rateLimited(
            GovernanceResource resource,
            TrafficTag trafficTag,
            Instant startedAt,
            Instant endedAt) {
        Map<String, String> tags = baseTags(resource, trafficTag);
        tags.put("governance_action", "rate_limited");
        tags.put("outcome", "rejected");
        tags.put("failure_category", "rate_limited");
        return event("governance.rate_limited", startedAt, endedAt, trafficTag,
                new FaultSignal(FaultSignal.FaultType.RATE_LIMITED, provider(resource), "", endedAt, true), tags);
    }

    /** Creates an event for a degraded or fallback response. */
    public static NexaryObservationEvent degraded(
            GovernanceResource resource,
            TrafficTag trafficTag,
            Instant startedAt,
            Instant endedAt) {
        Map<String, String> tags = baseTags(resource, trafficTag);
        tags.put("governance_action", "degraded");
        tags.put("outcome", "degraded");
        tags.put("failure_category", "degraded");
        return event("governance.degraded", startedAt, endedAt, trafficTag,
                new FaultSignal(FaultSignal.FaultType.DEGRADED, provider(resource), "", endedAt, true), tags);
    }

    /** Creates an event for a bulkhead or concurrency-limit rejection. */
    public static NexaryObservationEvent bulkheadRejected(
            GovernanceResource resource,
            TrafficTag trafficTag,
            Instant startedAt,
            Instant endedAt) {
        Map<String, String> tags = baseTags(resource, trafficTag);
        tags.put("governance_action", "bulkhead_rejected");
        tags.put("outcome", "rejected");
        tags.put("failure_category", "rejected");
        return event("governance.bulkhead.rejected", startedAt, endedAt, trafficTag,
                new FaultSignal(FaultSignal.FaultType.REJECTED, provider(resource), "", endedAt, true), tags);
    }

    private static NexaryObservationEvent event(
            String operation,
            Instant startedAt,
            Instant endedAt,
            TrafficTag trafficTag,
            FaultSignal faultSignal,
            Map<String, String> tags) {
        Instant safeEndedAt = endedAt == null ? Instant.now() : endedAt;
        return new NexaryObservationEvent(
                NexaryObservationEvent.EventCategory.GOVERNANCE,
                operation,
                startedAt == null ? safeEndedAt : startedAt,
                safeEndedAt,
                trafficTag,
                faultSignal,
                tags);
    }

    private static Map<String, String> baseTags(GovernanceResource resource, TrafficTag trafficTag) {
        GovernanceResource safeResource = resource == null ? GovernanceResource.service("default") : resource;
        TrafficTag safeTrafficTag = trafficTag == null ? TrafficTag.defaults() : trafficTag;
        Map<String, String> tags = new LinkedHashMap<>(safeResource.tags());
        tags.put("traffic_channel", safeTrafficTag.channel().name().toLowerCase(java.util.Locale.ROOT));
        tags.put("traffic_priority", safeTrafficTag.priority().name().toLowerCase(java.util.Locale.ROOT));
        return tags;
    }

    private static String provider(GovernanceResource resource) {
        return resource == null ? "unknown" : resource.provider();
    }

    private static String attemptBucket(int attempts) {
        if (attempts <= 0) {
            return "0";
        }
        if (attempts == 1) {
            return "1";
        }
        if (attempts == 2) {
            return "2";
        }
        if (attempts <= 5) {
            return "3_5";
        }
        if (attempts <= 10) {
            return "6_10";
        }
        return "gt_10";
    }
}
