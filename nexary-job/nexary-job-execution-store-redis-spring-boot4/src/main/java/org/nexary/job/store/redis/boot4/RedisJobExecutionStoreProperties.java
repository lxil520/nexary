package org.nexary.job.store.redis.boot4;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis-backed job execution store settings. */
@ConfigurationProperties(prefix = "nexary.job.execution.store.redis")
public class RedisJobExecutionStoreProperties {
    private boolean enabled = false;
    private String keyPrefix = "nexary:job:execution:";
    private Duration retention = Duration.ofDays(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix == null || keyPrefix.trim().isEmpty() ? "nexary:job:execution:" : keyPrefix;
    }

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention == null || retention.isZero() || retention.isNegative()
                ? Duration.ofDays(1)
                : retention;
    }
}
