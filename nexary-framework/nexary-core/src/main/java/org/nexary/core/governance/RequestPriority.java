package org.nexary.core.governance;

import org.nexary.core.context.TrafficTag;

/** Request priority used by governance policy selection. */
public enum RequestPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    /** Maps the existing traffic tag priority to the governance priority. */
    public static RequestPriority fromTrafficTag(TrafficTag trafficTag) {
        TrafficTag.Priority priority = trafficTag == null ? TrafficTag.Priority.NORMAL : trafficTag.priority();
        switch (priority) {
            case LOW:
                return LOW;
            case HIGH:
                return HIGH;
            case CRITICAL:
                return CRITICAL;
            case NORMAL:
            default:
                return NORMAL;
        }
    }
}
