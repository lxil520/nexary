package org.nexary.cache.redis.boot4;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis cache adapter settings. */
@ConfigurationProperties(prefix = "nexary.cache.redis")
public class RedisBoot4CacheProperties {
    private String lockPrefix = "nexary:lock:";
    private String fencingTokenPrefix = "nexary:fence:";
    private Duration defaultTtl = Duration.ofMinutes(10);
    private Duration localTtl = Duration.ofSeconds(30);
    private Duration lockRetryInterval = Duration.ofMillis(50);
    private boolean tieredEnabled = false;
    private boolean invalidationEnabled = true;
    private String invalidationChannel = "nexary:cache:invalidation";
    private String invalidationOriginId = UUID.randomUUID().toString();
    private boolean invalidationListenerAutoStart = true;
    private Duration invalidationListenerRecoveryBackoff = Duration.ofSeconds(5);
    private String providerName = "redis";

    public String getLockPrefix() {
        return lockPrefix;
    }

    public void setLockPrefix(String lockPrefix) {
        this.lockPrefix = lockPrefix;
    }

    public String getFencingTokenPrefix() {
        return fencingTokenPrefix;
    }

    public void setFencingTokenPrefix(String fencingTokenPrefix) {
        this.fencingTokenPrefix = fencingTokenPrefix;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(Duration defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public Duration getLockRetryInterval() {
        return lockRetryInterval;
    }

    public void setLockRetryInterval(Duration lockRetryInterval) {
        this.lockRetryInterval = lockRetryInterval;
    }

    public Duration getLocalTtl() {
        return localTtl;
    }

    public void setLocalTtl(Duration localTtl) {
        this.localTtl = localTtl;
    }

    public boolean isTieredEnabled() {
        return tieredEnabled;
    }

    public void setTieredEnabled(boolean tieredEnabled) {
        this.tieredEnabled = tieredEnabled;
    }

    public boolean isInvalidationEnabled() {
        return invalidationEnabled;
    }

    public void setInvalidationEnabled(boolean invalidationEnabled) {
        this.invalidationEnabled = invalidationEnabled;
    }

    public String getInvalidationChannel() {
        return invalidationChannel;
    }

    public void setInvalidationChannel(String invalidationChannel) {
        this.invalidationChannel = invalidationChannel;
    }

    public String getInvalidationOriginId() {
        return invalidationOriginId;
    }

    public void setInvalidationOriginId(String invalidationOriginId) {
        this.invalidationOriginId = invalidationOriginId;
    }

    public boolean isInvalidationListenerAutoStart() {
        return invalidationListenerAutoStart;
    }

    public void setInvalidationListenerAutoStart(boolean invalidationListenerAutoStart) {
        this.invalidationListenerAutoStart = invalidationListenerAutoStart;
    }

    public Duration getInvalidationListenerRecoveryBackoff() {
        return invalidationListenerRecoveryBackoff;
    }

    public void setInvalidationListenerRecoveryBackoff(Duration invalidationListenerRecoveryBackoff) {
        this.invalidationListenerRecoveryBackoff = invalidationListenerRecoveryBackoff;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = RedisBoot4ProtocolCacheProviderCondition.normalize(providerName);
    }
}
