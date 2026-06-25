package org.nexary.boot.governance.gateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the Nexary Gateway cancellation filter. */
@ConfigurationProperties(prefix = "nexary.governance.gateway")
public class GovernanceGatewayProperties {
    private boolean enabled = true;
    private Duration defaultTimeout = Duration.ofSeconds(30);
    private Duration cancelNotifyTimeout = Duration.ofMillis(500);
    private String cancelReceiverPath = "/nexary/governance/cancellations";
    private String receiverTokenHeaderName = "Nexary-Cancellation-Token";
    private String receiverToken;

    /** Returns whether the Gateway cancellation filter is enabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Sets whether the Gateway cancellation filter is enabled. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Returns the default request timeout when no inbound timeout header exists. */
    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    /** Sets the default request timeout when no inbound timeout header exists. */
    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout == null || defaultTimeout.isNegative() || defaultTimeout.isZero()
                ? Duration.ofSeconds(30)
                : defaultTimeout;
    }

    /** Returns the timeout used when notifying downstream cancellation receivers. */
    public Duration getCancelNotifyTimeout() {
        return cancelNotifyTimeout;
    }

    /** Sets the timeout used when notifying downstream cancellation receivers. */
    public void setCancelNotifyTimeout(Duration cancelNotifyTimeout) {
        this.cancelNotifyTimeout = cancelNotifyTimeout == null || cancelNotifyTimeout.isNegative()
                ? Duration.ofMillis(500)
                : cancelNotifyTimeout;
    }

    /** Returns the downstream receiver path appended to the routed service origin. */
    public String getCancelReceiverPath() {
        return cancelReceiverPath;
    }

    /** Sets the downstream receiver path appended to the routed service origin. */
    public void setCancelReceiverPath(String cancelReceiverPath) {
        this.cancelReceiverPath = cancelReceiverPath == null || cancelReceiverPath.trim().isEmpty()
                ? "/nexary/governance/cancellations"
                : cancelReceiverPath.trim();
    }

    /** Returns the optional request header name used for receiver token checks. */
    public String getReceiverTokenHeaderName() {
        return receiverTokenHeaderName;
    }

    /** Sets the optional request header name used for receiver token checks. */
    public void setReceiverTokenHeaderName(String receiverTokenHeaderName) {
        this.receiverTokenHeaderName = receiverTokenHeaderName == null || receiverTokenHeaderName.trim().isEmpty()
                ? "Nexary-Cancellation-Token"
                : receiverTokenHeaderName.trim();
    }

    /** Returns the optional receiver token forwarded to downstream applications. */
    public String getReceiverToken() {
        return receiverToken;
    }

    /** Sets the optional receiver token forwarded to downstream applications. */
    public void setReceiverToken(String receiverToken) {
        this.receiverToken = receiverToken;
    }
}
