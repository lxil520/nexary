package org.nexary.core.governance;

import org.nexary.core.context.TrafficTag;

/** Fixed low-cardinality traffic classes used by governance isolation policies. */
public enum GovernanceTrafficClass {
    /** User-facing online request traffic. */
    ONLINE,

    /** Offline task traffic that should not exhaust online windows. */
    OFFLINE,

    /** Batch traffic such as imports, backfills, or scheduled bulk work. */
    BATCH,

    /** Background repair, sync, or maintenance traffic. */
    BACKGROUND;

    /** Maps an existing traffic tag to a fixed governance traffic class. */
    public static GovernanceTrafficClass fromTrafficTag(TrafficTag trafficTag) {
        TrafficTag.Channel channel = trafficTag == null ? TrafficTag.Channel.ONLINE : trafficTag.channel();
        switch (channel) {
            case OFFLINE:
                return OFFLINE;
            case BATCH:
                return BATCH;
            case BACKGROUND:
                return BACKGROUND;
            case ONLINE:
            default:
                return ONLINE;
        }
    }

    /** Returns the stable lowercase diagnostic label. */
    public String diagnosticName() {
        return name().toLowerCase();
    }
}
