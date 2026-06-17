package org.nexary.cache.redis;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class CacheInvalidationEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        boolean tieredEnabled = environment.getProperty("nexary.cache.redis.tiered-enabled", Boolean.class, false);
        boolean invalidationEnabled =
                environment.getProperty("nexary.cache.redis.invalidation-enabled", Boolean.class, true);
        return tieredEnabled && invalidationEnabled;
    }
}
