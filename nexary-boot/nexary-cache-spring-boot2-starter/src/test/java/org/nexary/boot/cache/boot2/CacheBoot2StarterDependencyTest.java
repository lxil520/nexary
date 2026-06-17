package org.nexary.boot.cache.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.nexary.cache.CacheClient;
import org.nexary.cache.redis.boot2.RedisBoot2CacheAutoConfiguration;

class CacheBoot2StarterDependencyTest {
    @Test
    void starterExposesCacheApiAndBoot2RedisProviderLine() {
        assertThat(CacheClient.class).isNotNull();
        assertThat(RedisBoot2CacheAutoConfiguration.class).isNotNull();
    }
}
