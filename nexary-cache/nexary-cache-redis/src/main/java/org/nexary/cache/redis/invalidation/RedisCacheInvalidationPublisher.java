package org.nexary.cache.redis.invalidation;

import java.time.Instant;
import org.nexary.cache.invalidation.CacheInvalidationEvent;
import org.nexary.cache.invalidation.CacheInvalidationPublisher;
import org.nexary.cache.redis.RedisCacheObservation;
import org.nexary.cache.redis.RedisProtocolCacheProviderCondition;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis Pub/Sub invalidation publisher. */
public class RedisCacheInvalidationPublisher implements CacheInvalidationPublisher {
    private final StringRedisTemplate stringRedisTemplate;
    private final String channel;
    private final NexaryObservationPublisher observationPublisher;
    private final String providerName;

    public RedisCacheInvalidationPublisher(StringRedisTemplate stringRedisTemplate, String channel) {
        this(stringRedisTemplate, channel, NexaryObservationPublisher.noop());
    }

    public RedisCacheInvalidationPublisher(
            StringRedisTemplate stringRedisTemplate,
            String channel,
            NexaryObservationPublisher observationPublisher) {
        this(stringRedisTemplate, channel, observationPublisher, RedisProtocolCacheProviderCondition.REDIS);
    }

    public RedisCacheInvalidationPublisher(
            StringRedisTemplate stringRedisTemplate,
            String channel,
            NexaryObservationPublisher observationPublisher,
            String providerName) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.channel = channel;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
        this.providerName = RedisProtocolCacheProviderCondition.normalize(providerName);
    }

    @Override
    public void publish(CacheInvalidationEvent event) {
        Instant startedAt = Instant.now();
        try {
            stringRedisTemplate.convertAndSend(channel, RedisCacheInvalidationCodec.encode(event));
            RedisCacheObservation.publishForProvider(
                    observationPublisher, providerName, "cache.invalidation_publish", "none", "published", startedAt);
        } catch (RuntimeException ex) {
            RedisCacheObservation.publishForProvider(
                    observationPublisher,
                    providerName,
                    "cache.invalidation_publish",
                    "none",
                    "failure",
                    RedisCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }
}
