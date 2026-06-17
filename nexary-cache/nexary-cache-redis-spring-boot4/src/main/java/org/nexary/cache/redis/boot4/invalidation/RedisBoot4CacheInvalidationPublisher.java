package org.nexary.cache.redis.boot4.invalidation;

import java.time.Instant;
import org.nexary.cache.invalidation.CacheInvalidationEvent;
import org.nexary.cache.invalidation.CacheInvalidationPublisher;
import org.nexary.cache.redis.boot4.RedisBoot4CacheObservation;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis Pub/Sub invalidation publisher. */
public class RedisBoot4CacheInvalidationPublisher implements CacheInvalidationPublisher {
    private final StringRedisTemplate stringRedisTemplate;
    private final String channel;
    private final NexaryObservationPublisher observationPublisher;

    public RedisBoot4CacheInvalidationPublisher(StringRedisTemplate stringRedisTemplate, String channel) {
        this(stringRedisTemplate, channel, NexaryObservationPublisher.noop());
    }

    public RedisBoot4CacheInvalidationPublisher(
            StringRedisTemplate stringRedisTemplate,
            String channel,
            NexaryObservationPublisher observationPublisher) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.channel = channel;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public void publish(CacheInvalidationEvent event) {
        Instant startedAt = Instant.now();
        try {
            stringRedisTemplate.convertAndSend(channel, RedisBoot4CacheInvalidationCodec.encode(event));
            RedisBoot4CacheObservation.publish(
                    observationPublisher, "cache.invalidation_publish", "none", "published", startedAt);
        } catch (RuntimeException ex) {
            RedisBoot4CacheObservation.publish(
                    observationPublisher,
                    "cache.invalidation_publish",
                    "none",
                    "failure",
                    RedisBoot4CacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }
}
