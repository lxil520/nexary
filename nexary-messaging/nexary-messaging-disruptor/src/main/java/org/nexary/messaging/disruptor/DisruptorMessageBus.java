package org.nexary.messaging.disruptor;

import com.lmax.disruptor.BlockingWaitStrategy;
import java.util.Collections;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageGovernanceSupport;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageSubscriber;
import org.nexary.messaging.MessageSubscription;
import org.nexary.core.observation.NexaryObservationPublisher;

/** In-process ring-buffer message bus for low-latency local event dispatch. */
public class DisruptorMessageBus implements MessagePublisher, MessageSubscriber, AutoCloseable {
    private static final EventTranslatorOneArg<EventSlot, MessageEnvelope<?>> TRANSLATOR =
            (slot, sequence, envelope) -> slot.envelope = envelope;

    private final MessageConsumeExecutor consumeExecutor;
    private final List<Subscription<?>> subscriptions = new CopyOnWriteArrayList<>();
    private final Disruptor<EventSlot> disruptor;
    private final ScheduledExecutorService retryScheduler;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final NexaryObservationPublisher observationPublisher;

    public DisruptorMessageBus(int capacity, MessageConsumeExecutor consumeExecutor) {
        this(capacity, consumeExecutor, NexaryObservationPublisher.noop());
    }

    public DisruptorMessageBus(
            int capacity,
            MessageConsumeExecutor consumeExecutor,
            NexaryObservationPublisher observationPublisher) {
        this.consumeExecutor = Objects.requireNonNull(consumeExecutor, "consumeExecutor");
        this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
        this.disruptor = new Disruptor<>(
                EventSlot.EVENT_FACTORY,
                normalizeCapacity(capacity),
                task -> {
            Thread thread = new Thread(task, "nexary-disruptor-dispatcher-" + UUID.randomUUID());
            thread.setDaemon(true);
            return thread;
        },
                ProducerType.MULTI,
                new BlockingWaitStrategy());
        this.disruptor.handleEventsWith(new DispatchHandler());
        this.disruptor.start();
        this.retryScheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "nexary-disruptor-retry-" + UUID.randomUUID());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CompletionStage<MessagePublishResult> publish(MessageEnvelope<?> envelope) {
        java.util.Optional<MessagePublishResult> expired =
                MessageGovernanceSupport.rejectExpiredPublish(envelope, "disruptor", observationPublisher);
        if (expired.isPresent()) {
            return CompletableFuture.completedFuture(expired.get());
        }
        if (!running.get()) {
            MessageObservationSupport.publish(observationPublisher, "publish", "disruptor", "failure");
            return CompletableFuture.completedFuture(MessagePublishResult.failed("disruptor is closed", null));
        }
        try {
            disruptor.publishEvent(TRANSLATOR, envelope);
            MessageObservationSupport.publish(observationPublisher, "publish", "disruptor", "success");
            return CompletableFuture.completedFuture(MessagePublishResult.success(envelope.messageId()));
        } catch (RuntimeException ex) {
            MessageObservationSupport.publish(observationPublisher, "publish", "disruptor", "failure", Collections.emptyMap(), ex);
            return CompletableFuture.completedFuture(MessagePublishResult.failed(ex.getMessage(), null));
        }
    }

    @Override
    public <T> MessageSubscription subscribe(
            String topic,
            String consumerGroup,
            Class<T> payloadType,
            MessageConsumer<T> consumer) {
        Subscription<T> subscription = new Subscription<>(topic, consumerGroup, payloadType, consumer);
        subscriptions.add(subscription);
        return subscription;
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            retryScheduler.shutdownNow();
            disruptor.shutdown();
        }
    }

    private int normalizeCapacity(int capacity) {
        int normalized = Math.max(2, capacity);
        int highestBit = Integer.highestOneBit(normalized);
        return highestBit == normalized ? normalized : highestBit << 1;
    }

    private final class Subscription<T> implements MessageSubscription {
        private final String topic;
        private final String consumerGroup;
        private final Class<T> payloadType;
        private final MessageConsumer<T> consumer;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Subscription(String topic, String consumerGroup, Class<T> payloadType, MessageConsumer<T> consumer) {
            this.topic = topic;
            this.consumerGroup = consumerGroup;
            this.payloadType = payloadType;
            this.consumer = consumer;
        }

        @Override
        public String topic() {
            return topic;
        }

        @Override
        public String consumerGroup() {
            return consumerGroup;
        }

        @Override
        public void close() {
            active.set(false);
            subscriptions.remove(this);
        }

        private void tryConsume(MessageEnvelope<?> envelope) {
            if (!active.get() || !topic.equals(envelope.topic()) || !payloadType.isInstance(envelope.payload())) {
                return;
            }
            MessageObservationSupport.publish(observationPublisher, "dispatch", "disruptor", "success");
            MessageEnvelope<T> typed = new MessageEnvelope<>(
                    envelope.topic(),
                    envelope.key(),
                    payloadType.cast(envelope.payload()),
                    envelope.headers(),
                    envelope.deadline(),
                    envelope.trafficTag());
            MessageConsumeResult result = consumeExecutor.consume(typed, consumerGroup, consumer);
            MessageObservationSupport.publish(
                    observationPublisher, "consume", "disruptor", MessageObservationSupport.outcome(result));
            if (result.status() == MessageConsumeResult.ConsumeStatus.RETRY) {
                long delayMillis = result.retrySignal() == null ? 0 : result.retrySignal().backoff().toMillis();
                retryScheduler.schedule(() -> publish(typed), delayMillis, TimeUnit.MILLISECONDS);
            }
        }
    }

    private final class DispatchHandler implements EventHandler<EventSlot> {
        @Override
        public void onEvent(EventSlot event, long sequence, boolean endOfBatch) {
            MessageEnvelope<?> envelope = event.envelope;
            if (envelope != null) {
                for (Subscription<?> subscription : subscriptions) {
                    subscription.tryConsume(envelope);
                }
                event.envelope = null;
            }
        }
    }

    private static final class EventSlot {
        private static final EventFactory<EventSlot> EVENT_FACTORY = EventSlot::new;
        private MessageEnvelope<?> envelope;
    }
}
