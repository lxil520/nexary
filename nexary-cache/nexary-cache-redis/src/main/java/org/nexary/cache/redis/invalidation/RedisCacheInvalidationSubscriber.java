package org.nexary.cache.redis.invalidation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.nexary.cache.invalidation.CacheInvalidationListener;
import org.nexary.cache.redis.RedisCacheObservation;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Redis Pub/Sub invalidation subscriber that evicts matching local cache keys. */
public class RedisCacheInvalidationSubscriber implements MessageListener, SmartLifecycle {
    private final RedisMessageListenerContainer container;
    private final CacheInvalidationListener listener;
    private final String channel;
    private final String originId;
    private final boolean autoStartup;
    private final NexaryObservationPublisher observationPublisher;
    private volatile boolean running;

    public RedisCacheInvalidationSubscriber(
            RedisMessageListenerContainer container,
            CacheInvalidationListener listener,
            String channel,
            String originId,
            boolean autoStartup) {
        this(container, listener, channel, originId, autoStartup, NexaryObservationPublisher.noop());
    }

    public RedisCacheInvalidationSubscriber(
            RedisMessageListenerContainer container,
            CacheInvalidationListener listener,
            String channel,
            String originId,
            boolean autoStartup,
            NexaryObservationPublisher observationPublisher) {
        this.container = container;
        this.listener = listener;
        this.channel = channel;
        this.originId = originId;
        this.autoStartup = autoStartup;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        Instant startedAt = Instant.now();
        try {
            String payload = new String(message.getBody(), StandardCharsets.UTF_8);
            var event = RedisCacheInvalidationCodec.decode(payload);
            if (!originId.equals(event.originId())) {
                listener.onInvalidation(event);
                RedisCacheObservation.publish(
                        observationPublisher, "cache.invalidation_receive", "none", "received", startedAt);
                return;
            }
            RedisCacheObservation.publish(
                    observationPublisher, "cache.invalidation_receive", "none", "ignored", startedAt);
        } catch (RuntimeException ex) {
            RedisCacheObservation.publish(
                    observationPublisher,
                    "cache.invalidation_receive",
                    "none",
                    "failure",
                    RedisCacheObservation.failureCategory(ex),
                    startedAt);
            throw ex;
        }
    }

    @Override
    public void start() {
        if (!running) {
            container.addMessageListener(this, new ChannelTopic(channel));
            running = true;
        }
    }

    @Override
    public void stop() {
        if (running) {
            container.removeMessageListener(this, new ChannelTopic(channel));
            running = false;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isAutoStartup() {
        return autoStartup;
    }
}
