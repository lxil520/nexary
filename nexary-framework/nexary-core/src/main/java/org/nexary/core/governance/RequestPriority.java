package org.nexary.core.governance;

import org.nexary.core.context.TrafficTag;

/** Legacy request priority used by governance policy selection. */
public enum RequestPriority {
    LOW,
    NORMAL,
    HIGH,
    CRITICAL;

    /** Maps the existing traffic tag priority to the legacy request priority. */
    public static RequestPriority fromTrafficTag(TrafficTag trafficTag) {
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

    /** Returns the fixed three-bucket governance priority used by v0.14 isolation windows. */
    public GovernancePriority governancePriority() {
        return GovernancePriority.fromRequestPriority(this);
    }
}
