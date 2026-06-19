package org.nexary.cache.redis.boot4;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class RedisBoot4ProtocolCacheProviderCondition implements Condition {
    static final String REDIS = "redis";
    static final String VALKEY = "valkey";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = context.getEnvironment().getProperty("nexary.cache.provider", REDIS);
        String normalized = normalize(provider);
        return REDIS.equals(normalized) || VALKEY.equals(normalized);
    }

    static String normalize(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            return REDIS;
        }
        return provider.trim().toLowerCase();
    }
}
