package org.nexary.messaging.activemqclassic;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageObservationSupport;
import org.nexary.messaging.MessageSubscriber;
import org.nexary.messaging.MessageSubscriberReadiness;
import org.nexary.messaging.MessageSubscription;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** ActiveMQ Classic provider client auto-configuration used by starter and SPI-style imports. */
@AutoConfiguration(before = ActiveMqClassicMessagingAutoConfiguration.class)
@ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "activemq-classic")
@EnableConfigurationProperties(ActiveMqClassicMessagingProperties.class)
public class ActiveMqClassicProviderClientAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(ConnectionFactory.class)
    public ConnectionFactory activeMqClassicConnectionFactory(ActiveMqClassicMessagingProperties properties) {
        return new ActiveMQConnectionFactory(properties.getBrokerUrl());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MessageSubscriber.class)
    public MessageSubscriber activeMqClassicMessageSubscriber(
            ConnectionFactory connectionFactory,
            ActiveMqClassicMessagingProperties properties,
            ActiveMqClassicMessageListenerAdapterFactory adapterFactory,
            org.springframework.beans.factory.ObjectProvider<NexaryObservationPublisher> observationPublisher) {
        return new ActiveMqClassicSubscriber(
                connectionFactory,
                normalize(properties.getReceiveTimeout()),
                adapterFactory,
                observationPublisher.getIfAvailable(NexaryObservationPublisher::noop));
    }

    private static Duration normalize(Duration duration) {
        return duration == null || duration.isNegative() ? Duration.ofSeconds(1) : duration;
    }

    private static final class ActiveMqClassicSubscriber
            implements MessageSubscriber, MessageSubscriberReadiness, AutoCloseable {
        private final ConnectionFactory connectionFactory;
        private final Duration receiveTimeout;
        private final ActiveMqClassicMessageListenerAdapterFactory adapterFactory;
        private final NexaryObservationPublisher observationPublisher;
        private final List<ActiveMqClassicSubscription<?>> subscriptions = new CopyOnWriteArrayList<>();

        private ActiveMqClassicSubscriber(
                ConnectionFactory connectionFactory,
                Duration receiveTimeout,
                ActiveMqClassicMessageListenerAdapterFactory adapterFactory,
                NexaryObservationPublisher observationPublisher) {
            this.connectionFactory = connectionFactory;
            this.receiveTimeout = receiveTimeout;
            this.adapterFactory = adapterFactory;
            this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
        }

        @Override
        public <T> MessageSubscription subscribe(
                String topic,
                String consumerGroup,
                Class<T> payloadType,
                org.nexary.messaging.MessageConsumer<T> consumer) {
            ActiveMqClassicMessageListenerAdapter<T> adapter =
                    adapterFactory.create(consumerGroup, payloadType, consumer);
            ActiveMqClassicSubscription<T> subscription = new ActiveMqClassicSubscription<>(
                    connectionFactory,
                    receiveTimeout,
                    topic,
                    consumerGroup,
                    adapter,
                    observationPublisher);
            subscriptions.add(subscription);
            subscription.start();
            return subscription;
        }

        @Override
        public boolean isReady() {
            return !subscriptions.isEmpty() && subscriptions.stream().allMatch(ActiveMqClassicSubscription::isReady);
        }

        @Override
        public void close() {
            subscriptions.forEach(MessageSubscription::close);
        }
    }

    private static final class ActiveMqClassicSubscription<T> implements MessageSubscription {
        private final ConnectionFactory connectionFactory;
        private final Duration receiveTimeout;
        private final String topic;
        private final String consumerGroup;
        private final ActiveMqClassicMessageListenerAdapter<T> adapter;
        private final NexaryObservationPublisher observationPublisher;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicBoolean ready = new AtomicBoolean(false);
        private final ExecutorService worker;

        private ActiveMqClassicSubscription(
                ConnectionFactory connectionFactory,
                Duration receiveTimeout,
                String topic,
                String consumerGroup,
                ActiveMqClassicMessageListenerAdapter<T> adapter,
                NexaryObservationPublisher observationPublisher) {
            this.connectionFactory = connectionFactory;
            this.receiveTimeout = receiveTimeout;
            this.topic = topic;
            this.consumerGroup = consumerGroup;
            this.adapter = adapter;
            this.observationPublisher = observationPublisher == null ? NexaryObservationPublisher.noop() : observationPublisher;
            this.worker = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "nexary-activemq-classic-" + consumerGroup);
                thread.setDaemon(true);
                return thread;
            });
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
            running.set(false);
            worker.shutdownNow();
        }

        private boolean isReady() {
            return ready.get();
        }

        private void start() {
            worker.submit(this::receiveLoop);
        }

        private void receiveLoop() {
            try (Connection connection = connectionFactory.createConnection();
                    Session session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
                    MessageConsumer consumer = consumer(session)) {
                connection.start();
                ready.set(true);
                while (running.get()) {
                    Message message = consumer.receive(receiveTimeout.toMillis());
                    if (message == null) {
                        continue;
                    }
                    handle(session, message);
                }
            } catch (JMSException ex) {
                MessageObservationSupport.publish(
                        observationPublisher, "provider.consume_status", "activemq_classic", "failure",
                        MessageObservationSupport.boundaryTags("jms_receive_loop"),
                        ex);
            }
        }

        private MessageConsumer consumer(Session session) throws JMSException {
            Queue queue = session.createQueue(topic);
            return session.createConsumer(queue);
        }

        private void handle(Session session, Message message) throws JMSException {
            MessageConsumeResult result = adapter.onMessage(topic, message);
            if (result.status() == MessageConsumeResult.ConsumeStatus.RETRY
                    || result.status() == MessageConsumeResult.ConsumeStatus.FAILED) {
                session.recover();
                MessageObservationSupport.publish(
                        observationPublisher,
                        "provider.recover",
                        "activemq_classic",
                        "retry",
                        MessageObservationSupport.boundaryTags("session_recover"),
                        null);
                sleep(result);
                return;
            }
            message.acknowledge();
            MessageObservationSupport.publish(
                    observationPublisher,
                    "provider.ack",
                    "activemq_classic",
                    "success",
                    MessageObservationSupport.boundaryTags("client_acknowledge"),
                    null);
        }

        private void sleep(MessageConsumeResult result) {
            long delayMillis = result.retrySignal() == null ? 0 : result.retrySignal().backoff().toMillis();
            if (delayMillis <= 0) {
                return;
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
