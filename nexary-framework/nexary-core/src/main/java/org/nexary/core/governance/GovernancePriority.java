package org.nexary.core.governance;

import org.nexary.core.context.TrafficTag;

/** Fixed low-cardinality priority buckets used by governance isolation policies. */
public enum GovernancePriority {
    /** Prefer online or control traffic. */
    HIGH,

    /** Default traffic priority. */
    NORMAL,

    /** Best-effort traffic that should be isolated first under pressure. */
    LOW;

    /** Maps an existing traffic tag priority to a fixed governance priority. */
    public static GovernancePriority fromTrafficTag(TrafficTag trafficTag) {
        TrafficTag.Priority priority = trafficTag == null ? TrafficTag.Priority.NORMAL : trafficTag.priority();
        switch (priority) {
            case LOW:
                return LOW;
            case HIGH:
            case CRITICAL:
                return HIGH;
            case NORMAL:
            default:
                return NORMAL;
        }
    }

    /** Maps the legacy request priority type to the fixed governance priority. */
    public static GovernancePriority fromRequestPriority(RequestPriority priority) {
        if (priority == null) {
            return NORMAL;
        }
        switch (priority) {
            case LOW:
                return LOW;
            case HIGH:
            case CRITICAL:
                return HIGH;
            case NORMAL:
            default:
                return NORMAL;
        }
    }

    /** Returns the legacy request priority for compatibility with older APIs. */
    public RequestPriority toRequestPriority() {
        switch (this) {
            case LOW:
                return RequestPriority.LOW;
            case HIGH:
                return RequestPriority.HIGH;
            case NORMAL:
            default:
                return RequestPriority.NORMAL;
        }
    }

    /** Returns the matching traffic tag priority. */
    public TrafficTag.Priority toTrafficTagPriority() {
        switch (this) {
            case LOW:
                return TrafficTag.Priority.LOW;
            case HIGH:
                return TrafficTag.Priority.HIGH;
            case NORMAL:
            default:
                return TrafficTag.Priority.NORMAL;
        }
    }

    /** Returns the stable lowercase diagnostic label. */
    public String diagnosticName() {
        return name().toLowerCase();
    }
}
