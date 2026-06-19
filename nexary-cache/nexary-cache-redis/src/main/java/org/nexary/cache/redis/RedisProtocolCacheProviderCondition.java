package org.nexary.cache.redis;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public final class RedisProtocolCacheProviderCondition implements Condition {
    public static final String REDIS = "redis";
    public static final String VALKEY = "valkey";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = selectedProvider(context);
        return REDIS.equals(provider) || VALKEY.equals(provider);
    }

    static String selectedProvider(ConditionContext context) {
        String provider = context.getEnvironment().getProperty("nexary.cache.provider", REDIS);
        return normalize(provider);
    }

    public static String normalize(String provider) {
        if (provider == null || provider.trim().isEmpty()) {
            return REDIS;
        }
        return provider.trim().toLowerCase();
    }
}
