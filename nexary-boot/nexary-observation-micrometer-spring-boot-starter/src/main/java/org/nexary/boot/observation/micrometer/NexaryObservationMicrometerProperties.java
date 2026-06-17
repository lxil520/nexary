package org.nexary.boot.observation.micrometer;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the Nexary Micrometer observation bridge. */
@ConfigurationProperties(prefix = "nexary.observation.micrometer")
public class NexaryObservationMicrometerProperties {
    private boolean enabled = true;
    private String counterName = "nexary.observation.events.total";
    private String timerName = "nexary.observation.events.duration";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCounterName() {
        return counterName;
    }

    public void setCounterName(String counterName) {
        this.counterName = counterName;
    }

    public String getTimerName() {
        return timerName;
    }

    public void setTimerName(String timerName) {
        this.timerName = timerName;
    }
}
