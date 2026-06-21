package org.nexary.messaging.redis.boot2;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageGovernanceSupport;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSerializer;
import org.nexary.messaging.MessageSubscriber;
import org.nexary.messaging.MessageSubscriberReadiness;
import org.nexary.messaging.MessageSubscription;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Spring Boot 2 Redis ready/processing list queue with ack, retry requeue and stale processing recovery. */
public class RedisBoot2MessageQueue implements MessagePublisher, MessageSubscriber, MessageSubscriberReadiness, AutoCloseable {
    private final RedisBoot2QueueProcessingStore processingStore;
    private final MessageSerializer serializer;
    private final MessageConsumeExecutor consumeExecutor;
    private final RedisBoot2MessagingProperties properties;
    private final NexaryObservationPublisher observationPublisher;
    private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "nexary-redis-boot2-queue-retry-" + UUID.randomUUID());
        thread.setDaemon(true);
        return thread;
    });

    public RedisBoot2MessageQueue(
            StringRedisTemplate stringRedisTemplate,
            MessageSerializer serializer,
            MessageConsumeExecutor consumeExecutor,
            RedisBoot2MessagingProperties properties) {
        this(stringRedisTemplate, serializer, consumeExecutor, properties, NexaryObservationPublisher.noop());
    }

    public RedisBoot2MessageQueue(
            StringRedisTemplate stringRedisTemplate,
            MessageSerializer serializer,
            MessageConsumeExecutor consumeExecutor,
            RedisBoot2MessagingProperties properties,
            NexaryObservationPublisher observationPublisher) {
        this(
                new RedisBoot2StringTemplateQueueProcessingStore(stringRedisTemplate, properties),
                serializer,
                consumeExecutor,
                properties,
                observationPublisher);
    }

    RedisBoot2MessageQueue(
            RedisBoot2QueueProcessingStore processingStore,
            MessageSerializer serializer,
            MessageConsumeExecutor consumeExecutor,
            RedisBoot2MessagingProperties properties) {
        this(processingStore, serializer, consumeExecutor, properties, NexaryObservationPublisher.noop());
    }

    RedisBoot2MessageQueue(
            RedisBoot2QueueProcessingStore processingStore,
            MessageSerializer serializer,
            MessageConsumeExecutor consumeExecutor,
            RedisBoot2MessagingProperties properties,
            NexaryObservationPublisher observationPublisher) {
        this.processingStore = processingStore;
        this.serializer = serializer;
        this.consumeExecutor = consumeExecutor;
        this.properties = properties;
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
    }

    @Override
    public CompletionStage<MessagePublishResult> publish(MessageEnvelope<?> envelope) {
        java.util.Optional<MessagePublishResult> expired =
                MessageGovernanceSupport.rejectExpiredPublish(envelope, "redis", observationPublisher);
        if (expired.isPresent()) {
            return CompletableFuture.completedFuture(expired.get());
        }
        String encoded = encode(envelope);
        boolean accepted = processingStore.enqueueReady(envelope.topic(), encoded);
        MessageObservationSupport.publish(
                observationPublisher, "publish", "redis", accepted ? "success" : "failure");
        return CompletableFuture.completedFuture(accepted
                ? MessagePublishResult.success(envelope.messageId())
                : MessagePublishResult.failed("redis queue push failed", null));
    }

    @Override
    public <T> MessageSubscription subscribe(
            String topic,
            String consumerGroup,
            Class<T> payloadType,
            MessageConsumer<T> consumer) {
        Subscription<T> subscription = new Subscription<>(topic, consumerGroup, payloadType, consumer);
        subscriptions.add(subscription);
        subscription.start();
        return subscription;
    }

    @Override
    public void close() {
        for (MessageSubscription subscription : subscriptions) {
            subscription.close();
        }
        retryScheduler.shutdownNow();
    }

    @Override
    public boolean isReady() {
        return !subscriptions.isEmpty();
    }

    private String encode(MessageEnvelope<?> envelope) {
        byte[] payload = serializer.serialize(envelope.payload());
        String messageId = envelope.messageId();
        String key = envelope.key() == null ? "" : envelope.key();
        String data = Base64.getEncoder().encodeToString(payload);
        String headers = Base64.getEncoder().encodeToString(
                encodeHeaders(MessageGovernanceSupport.governedHeaders(envelope)).getBytes(StandardCharsets.UTF_8));
        return messageId + "|" + key + "|" + data + "|" + headers;
    }

    private <T> MessageEnvelope<T> decode(String topic, Class<T> payloadType, String encoded) {
        String[] parts = encoded.split("\\|", 4);
        String messageId = parts[0];
        String key = parts.length > 1 && !isBlank(parts[1]) ? parts[1] : null;
        byte[] payload = parts.length > 2
                ? Base64.getDecoder().decode(parts[2].getBytes(StandardCharsets.UTF_8))
                : new byte[0];
        T deserialized = serializer.deserialize(payload, payloadType);
        Map<String, String> headers = parts.length > 3
                ? decodeHeaders(new String(Base64.getDecoder().decode(parts[3]), StandardCharsets.UTF_8))
                : Collections.emptyMap();
        Map<String, String> effectiveHeaders = new LinkedHashMap<>(headers);
        if (!effectiveHeaders.containsKey(MessageEnvelope.MESSAGE_ID_HEADER)) {
            effectiveHeaders.put(MessageEnvelope.MESSAGE_ID_HEADER, messageId);
        }
        return new MessageEnvelope<>(
                topic,
                key,
                deserialized,
                effectiveHeaders,
                MessageGovernanceSupport.deadlineFromHeaders(effectiveHeaders),
                null);
    }

    private String encodeHeaders(Map<String, String> headers) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder
                    .append(Base64.getEncoder().encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8)))
                    .append(":")
                    .append(Base64.getEncoder().encodeToString(entry.getValue().getBytes(StandardCharsets.UTF_8)))
                    .append("\n");
        }
        return builder.toString();
    }

    private Map<String, String> decodeHeaders(String encoded) {
        if (isBlank(encoded)) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        String[] lines = encoded.split("\n");
        for (String line : lines) {
            if (isBlank(line)) {
                continue;
            }
            String[] parts = line.split(":", 2);
            if (parts.length == 2) {
                headers.put(
                        new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8),
                        new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            }
        }
        return headers;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private final class Subscription<T> implements MessageSubscription {
        private final String topic;
        private final String consumerGroup;
        private final Class<T> payloadType;
        private final MessageConsumer<T> consumer;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final ExecutorService worker;
        private long nextRecoveryNanos;

        private Subscription(String topic, String consumerGroup, Class<T> payloadType, MessageConsumer<T> consumer) {
            this.topic = topic;
            this.consumerGroup = consumerGroup;
            this.payloadType = payloadType;
            this.consumer = consumer;
            this.worker = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "nexary-redis-boot2-queue-" + topic + "-" + consumerGroup);
                thread.setDaemon(true);
                return thread;
            });
            this.nextRecoveryNanos = System.nanoTime();
        }

        @Override
        public void close() {
            running.set(false);
            subscriptions.remove(this);
            worker.shutdownNow();
        }

        private void start() {
            worker.submit(this::pollLoop);
        }

        @Override
        public String topic() {
            return topic;
        }

        @Override
        public String consumerGroup() {
            return consumerGroup;
        }

        private void pollLoop() {
            while (running.get()) {
                recoverStaleIfDue();
                String encoded = processingStore.moveReadyToProcessing(topic, consumerGroup, properties.getPollTimeout());
                if (encoded == null) {
                    continue;
                }
                MessageEnvelope<T> envelope = decode(topic, payloadType, encoded);
                MessageConsumeResult result = consumeExecutor.consume(envelope, consumerGroup, consumer);
                MessageObservationSupport.publish(
                        observationPublisher, "consume", "redis", MessageObservationSupport.outcome(result));
                if (result.status() == MessageConsumeResult.ConsumeStatus.RETRY) {
                    Duration backoff = result.retrySignal() == null ? Duration.ZERO : result.retrySignal().backoff();
                    processingStore.extendLease(topic, consumerGroup, encoded, backoff);
                    retryScheduler.schedule(
                            () -> {
                                processingStore.requeue(topic, consumerGroup, encoded);
                                MessageObservationSupport.publish(
                                        observationPublisher, "provider.requeue", "redis", "success");
                            },
                            backoff.toMillis(),
                            TimeUnit.MILLISECONDS);
                } else if (result.status() == MessageConsumeResult.ConsumeStatus.SUCCESS
                        || result.status() == MessageConsumeResult.ConsumeStatus.DUPLICATE
                        || result.status() == MessageConsumeResult.ConsumeStatus.DEAD_LETTER) {
                    processingStore.ack(topic, consumerGroup, encoded);
                    MessageObservationSupport.publish(
                            observationPublisher, "provider.ack", "redis", "success");
                }
            }
        }

        private void recoverStaleIfDue() {
            long now = System.nanoTime();
            if (now >= nextRecoveryNanos) {
                int recovered = processingStore.recoverStale(topic, consumerGroup);
                MessageObservationSupport.publish(
                        observationPublisher,
                        "provider.recovery",
                        "redis",
                        recovered > 0 ? "success" : "noop");
                nextRecoveryNanos = now + properties.getProcessingRecoveryInterval().toNanos();
            }
        }
    }
}
