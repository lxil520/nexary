package org.nexary.cache.redis;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.cache.invalidation.CacheInvalidationEvent;
import org.nexary.cache.invalidation.CacheInvalidationListener;
import org.nexary.core.governance.GovernanceContext;
import org.nexary.core.governance.GovernanceResource;
import org.nexary.governance.runtime.GovernanceRuntime;

class GovernedCacheClient implements CacheClient {
    static final String RESOURCE_NAME = "cache-client";

    private final CacheClient delegate;
    private final GovernanceRuntime governanceRuntime;
    private final String provider;

    GovernedCacheClient(CacheClient delegate, GovernanceRuntime governanceRuntime, String provider) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.governanceRuntime = Objects.requireNonNull(governanceRuntime, "governanceRuntime");
        this.provider = hasText(provider) ? provider.trim() : "unknown";
    }

    static CacheClient wrap(CacheClient delegate, GovernanceRuntime governanceRuntime, String provider) {
        if (governanceRuntime == null) {
            return delegate;
        }
        if (delegate instanceof CacheInvalidationListener) {
            return new GovernedInvalidationCacheClient(delegate, governanceRuntime, provider);
        }
        return new GovernedCacheClient(delegate, governanceRuntime, provider);
    }

    @Override
    public <T> Optional<T> get(CacheKey key, Class<T> type) {
        return execute("cache.get", () -> delegate.get(key, type));
    }

    @Override
    public void put(CacheKey key, Object value, Duration ttl) {
        execute("cache.put", () -> {
            delegate.put(key, value, ttl);
            return null;
        });
    }

    @Override
    public boolean putIfAbsent(CacheKey key, Object value, Duration ttl) {
        return execute("cache.put_if_absent", () -> delegate.putIfAbsent(key, value, ttl));
    }

    @Override
    public Map<CacheKey, Object> getAll(Collection<CacheKey> keys) {
        return execute("cache.batch_get", () -> delegate.getAll(keys));
    }

    @Override
    public void putAll(Map<CacheKey, ?> values, Duration ttl) {
        execute("cache.batch_put", () -> {
            delegate.putAll(values, ttl);
            return null;
        });
    }

    @Override
    public boolean delete(CacheKey key) {
        return execute("cache.delete", () -> delegate.delete(key));
    }

    @Override
    public boolean expire(CacheKey key, Duration ttl) {
        return execute("cache.expire", () -> delegate.expire(key, ttl));
    }

    @Override
    public Optional<LockHandle> tryLock(CacheKey key, Duration waitTime, Duration leaseTime) {
        return execute("cache.lock_acquire", () -> delegate.tryLock(key, waitTime, leaseTime));
    }

    @Override
    public <T> T cacheAside(CacheKey key, Class<T> type, Duration ttl, Supplier<T> loader) {
        return execute("cache.cache_aside", () -> delegate.cacheAside(key, type, ttl, loader));
    }

    private <T> T execute(String operation, Callable<T> action) {
        try {
            return governanceRuntime.execute(context(operation), action);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("cache governance execution failed", ex);
        }
    }

    private GovernanceContext context(String operation) {
        GovernanceResource resource = GovernanceResource.cache(RESOURCE_NAME, provider, operation);
        Optional<GovernanceContext> current = GovernanceContext.current();
        GovernanceContext.Builder builder = GovernanceContext.builder().resource(resource);
        current.ifPresent(context -> {
            builder.trafficTag(context.trafficTag())
                    .priority(context.priority());
            context.deadline().ifPresent(builder::deadline);
            context.attributes().forEach(builder::attribute);
        });
        return builder.build();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class GovernedInvalidationCacheClient extends GovernedCacheClient
            implements CacheInvalidationListener {
        private final CacheInvalidationListener invalidationListener;

        private GovernedInvalidationCacheClient(CacheClient delegate, GovernanceRuntime governanceRuntime, String provider) {
            super(delegate, governanceRuntime, provider);
            this.invalidationListener = (CacheInvalidationListener) delegate;
        }

        @Override
        public void onInvalidation(CacheInvalidationEvent event) {
            invalidationListener.onInvalidation(event);
        }
    }
}
