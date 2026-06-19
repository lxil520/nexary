package org.nexary.messaging.activemqclassic;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationEvent;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.InMemoryMessageDeadLetterPublisher;
import org.nexary.messaging.MessageBackoffStrategy;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.MessageRetryPolicy;
import org.nexary.messaging.MessageSubscriber;
import org.nexary.messaging.MessageSubscriberReadiness;
import org.nexary.messaging.MessageSubscription;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;

class ActiveMqClassicBrokerIntegrationTest {
    private MessageSubscription subscription;

    @BeforeEach
    void resetSubscription() {
        subscription = null;
    }

    @AfterEach
    void closeSubscription() {
        if (subscription != null) {
            subscription.close();
        }
    }

    @Test
    void publishesConsumesAndDeduplicatesWithEmbeddedActiveMqClassicBroker() throws Exception {
        String brokerName = "nexary-activemq-" + UUID.randomUUID();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl(brokerName));
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        MessageConsumeExecutor executor = executor(deadLetters, events, 3);
        ActiveMqClassicMessageListenerAdapterFactory adapterFactory = new ActiveMqClassicMessageListenerAdapterFactory(
                executor,
                new DefaultStringMessageSerializer(),
                events::add);
        MessageSubscriber subscriber = new ActiveMqClassicSubscriberFactory().create(connectionFactory, adapterFactory, events);
        MessagePublisher publisher = new ActiveMqClassicMessagePublisher(
                connectionFactory,
                new DefaultStringMessageSerializer(),
                events::add);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        String topic = "nexary.activemq." + UUID.randomUUID();
        String messageId = "active-success-" + UUID.randomUUID();

        subscription = subscriber.subscribe(topic, "active-consumer", String.class, envelope -> {
            calls.incrementAndGet();
            latch.countDown();
        });
        waitForReady(subscriber);

        MessagePublishResult first = publisher.publish(envelope(topic, messageId, "payload"))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);
        MessagePublishResult duplicate = publisher.publish(envelope(topic, messageId, "payload"))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertThat(first.status()).isEqualTo(MessagePublishResult.PublishStatus.SUCCESS);
        assertThat(duplicate.status()).isEqualTo(MessagePublishResult.PublishStatus.SUCCESS);
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        Thread.sleep(300L);
        assertThat(calls).hasValue(1);
        assertThat(deadLetters.records()).isEmpty();
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("publish", "consume", "provider.ack", "dedup.claim");
        assertNoHighCardinalityTags(events, messageId, topic);
    }

    @Test
    void retriesThenPublishesOneTerminalDeadLetterWithEmbeddedActiveMqClassicBroker() throws Exception {
        String brokerName = "nexary-activemq-" + UUID.randomUUID();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(brokerUrl(brokerName));
        List<NexaryObservationEvent> events = new CopyOnWriteArrayList<>();
        InMemoryMessageDeadLetterPublisher deadLetters = MessageDeadLetterPublisher.inMemory();
        MessageConsumeExecutor executor = executor(deadLetters, events, 2);
        ActiveMqClassicMessageListenerAdapterFactory adapterFactory = new ActiveMqClassicMessageListenerAdapterFactory(
                executor,
                new DefaultStringMessageSerializer(),
                events::add);
        MessageSubscriber subscriber = new ActiveMqClassicSubscriberFactory().create(connectionFactory, adapterFactory, events);
        MessagePublisher publisher = new ActiveMqClassicMessagePublisher(
                connectionFactory,
                new DefaultStringMessageSerializer(),
                events::add);
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        String topic = "nexary.activemq.failure." + UUID.randomUUID();
        String messageId = "active-failure-" + UUID.randomUUID();

        subscription = subscriber.subscribe(topic, "active-consumer", String.class, envelope -> {
            attempts.incrementAndGet();
            if (attempts.get() >= 2) {
                terminal.countDown();
            }
            throw new IllegalStateException("boom");
        });
        waitForReady(subscriber);

        publisher.publish(envelope(topic, messageId, "payload"))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertThat(terminal.await(10, TimeUnit.SECONDS)).isTrue();
        waitForDeadLetters(deadLetters, 1);
        assertThat(attempts).hasValue(2);
        assertThat(deadLetters.records()).hasSize(1);
        assertThat(deadLetters.records().get(0).messageId()).isEqualTo(messageId);
        assertThat(events).extracting(NexaryObservationEvent::operation)
                .contains("provider.recover", "provider.ack", "retry.schedule", "deadletter.publish");
        assertNoHighCardinalityTags(events, messageId, topic);
    }

    private static MessageConsumeExecutor executor(
            MessageDeadLetterPublisher deadLetterPublisher,
            List<NexaryObservationEvent> events,
            int maxAttempts) {
        return new MessageConsumeExecutor(
                Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                Duration.ofMinutes(5),
                List.of(),
                new MessageRetryPolicy(maxAttempts, Duration.ZERO, MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                deadLetterPublisher,
                events::add);
    }

    private static MessageEnvelope<String> envelope(String topic, String messageId, String payload) {
        return new MessageEnvelope<>(
                topic,
                "app-42",
                payload,
                Map.of(MessageEnvelope.MESSAGE_ID_HEADER, messageId, "tenant", "demo"),
                null,
                null);
    }

    private static String brokerUrl(String brokerName) {
        return "vm://" + brokerName + "?broker.persistent=false&broker.useJmx=false";
    }

    private static void waitForReady(MessageSubscriber subscriber) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (subscriber instanceof MessageSubscriberReadiness
                    && ((MessageSubscriberReadiness) subscriber).isReady()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertThat(subscriber).isInstanceOf(MessageSubscriberReadiness.class);
        assertThat(((MessageSubscriberReadiness) subscriber).isReady()).isTrue();
    }

    private static void waitForDeadLetters(InMemoryMessageDeadLetterPublisher deadLetters, int expected)
            throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (deadLetters.records().size() == expected) {
                return;
            }
            Thread.sleep(50L);
        }
        assertThat(deadLetters.records()).hasSize(expected);
    }

    private static void assertNoHighCardinalityTags(
            List<NexaryObservationEvent> events,
            String messageId,
            String topic) {
        events.forEach(event -> {
            assertThat(event.tags()).doesNotContainKeys(
                    "message_id",
                    "payload",
                    "topic",
                    "consumer_group",
                    "exception_message",
                    "stack_trace");
            assertThat(event.tags().values()).doesNotContain(messageId, topic, "payload", "active-consumer");
        });
    }

    private static final class ActiveMqClassicSubscriberFactory extends ActiveMqClassicProviderClientAutoConfiguration {
        private MessageSubscriber create(
                ActiveMQConnectionFactory connectionFactory,
                ActiveMqClassicMessageListenerAdapterFactory adapterFactory,
                List<NexaryObservationEvent> events) {
            ActiveMqClassicMessagingProperties properties = new ActiveMqClassicMessagingProperties();
            properties.setReceiveTimeout(Duration.ofMillis(100));
            return activeMqClassicMessageSubscriber(
                    connectionFactory,
                    properties,
                    adapterFactory,
                    new SingletonObjectProvider<>(events::add));
        }
    }

    private static final class SingletonObjectProvider<T> implements ObjectProvider<T> {
        private final T value;

        private SingletonObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) throws BeansException {
            return value;
        }

        @Override
        public T getIfAvailable() throws BeansException {
            return value;
        }

        @Override
        public T getIfUnique() throws BeansException {
            return value;
        }

        @Override
        public T getObject() throws BeansException {
            return value;
        }

        @Override
        public Iterator<T> iterator() {
            return List.of(value).iterator();
        }
    }
}
