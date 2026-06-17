package org.nexary.boot.cache.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.redis.boot4.RedisBoot4CacheAutoConfiguration;

class CacheBoot4StarterDependencyTest {
    @Test
    void starterExposesCacheApiAndBoot4RedisProviderLine() {
        assertThat(CacheClient.class).isNotNull();
        assertThat(RedisBoot4CacheAutoConfiguration.class).isNotNull();
    }
}
