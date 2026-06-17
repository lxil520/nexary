package org.nexary.messaging.kafka;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageSubscriber;
import org.nexary.messaging.MessageSubscriberReadiness;
import org.nexary.messaging.MessageSubscription;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Kafka provider client auto-configuration used by starter and SPI-style provider imports. */
@AutoConfiguration(before = KafkaMessagingAutoConfiguration.class)
@ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "kafka")
@EnableConfigurationProperties(KafkaMessagingProperties.class)
public class KafkaProviderClientAutoConfiguration {
    @Bean(name = "kafkaTemplate", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "kafkaTemplate")
    public Object kafkaTemplate(KafkaMessagingProperties properties) {
        return new NexaryKafkaTemplate(properties.getBootstrapServers());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MessageSubscriber.class)
    public MessageSubscriber kafkaMessageSubscriber(
            KafkaMessagingProperties properties,
            KafkaMessageListenerAdapterFactory adapterFactory) {
        return new NexaryKafkaSubscriber(properties.getBootstrapServers(), adapterFactory);
    }

    private static final class NexaryKafkaTemplate implements AutoCloseable {
        private final KafkaProducer<String, byte[]> producer;

        private NexaryKafkaTemplate(String bootstrapServers) {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
            config.put(ProducerConfig.ACKS_CONFIG, "all");
            this.producer = new KafkaProducer<>(config);
        }

        public CompletableFuture<RecordMetadata> send(ProducerRecord<String, byte[]> record) {
            CompletableFuture<RecordMetadata> future = new CompletableFuture<>();
            producer.send(record, (metadata, error) -> {
                if (error == null) {
                    future.complete(metadata);
                } else {
                    future.completeExceptionally(error);
                }
            });
            return future;
        }

        @Override
        public void close() {
            producer.close();
        }
    }

    private static final class NexaryKafkaSubscriber implements MessageSubscriber, MessageSubscriberReadiness, AutoCloseable {
        private final String bootstrapServers;
        private final KafkaMessageListenerAdapterFactory adapterFactory;
        private final List<NexaryKafkaSubscription<?>> subscriptions = new CopyOnWriteArrayList<>();

        private NexaryKafkaSubscriber(String bootstrapServers, KafkaMessageListenerAdapterFactory adapterFactory) {
            this.bootstrapServers = bootstrapServers;
            this.adapterFactory = adapterFactory;
        }

        @Override
        public <T> MessageSubscription subscribe(
                String topic,
                String consumerGroup,
                Class<T> payloadType,
                MessageConsumer<T> consumer) {
            KafkaMessageListenerAdapter<T> adapter = adapterFactory.create(consumerGroup, payloadType, consumer);
            NexaryKafkaSubscription<T> subscription =
                    new NexaryKafkaSubscription<>(bootstrapServers, topic, consumerGroup, adapter);
            subscriptions.add(subscription);
            subscription.start();
            return subscription;
        }

        @Override
        public boolean isReady() {
            return !subscriptions.isEmpty() && subscriptions.stream().allMatch(NexaryKafkaSubscription::isReady);
        }

        @Override
        public void close() {
            subscriptions.forEach(MessageSubscription::close);
        }
    }

    private static final class NexaryKafkaSubscription<T> implements MessageSubscription {
        private final String topic;
        private final String consumerGroup;
        private final KafkaMessageListenerAdapter<T> adapter;
        private final KafkaConsumer<String, byte[]> consumer;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicBoolean ready = new AtomicBoolean(false);
        private final ExecutorService worker;

        private NexaryKafkaSubscription(
                String bootstrapServers,
                String topic,
                String consumerGroup,
                KafkaMessageListenerAdapter<T> adapter) {
            this.topic = topic;
            this.consumerGroup = consumerGroup;
            this.adapter = adapter;
            Map<String, Object> config = new HashMap<>();
            config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            config.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
            config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
            config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
            config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
            config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            this.consumer = new KafkaConsumer<>(config);
            this.worker = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "nexary-kafka-consumer-" + consumerGroup);
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
            consumer.wakeup();
            worker.shutdownNow();
        }

        private boolean isReady() {
            return ready.get();
        }

        private void start() {
            worker.submit(this::pollLoop);
        }

        private void pollLoop() {
            try {
                consumer.subscribe(Collections.singletonList(topic));
                while (running.get()) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                    if (!consumer.assignment().isEmpty()) {
                        ready.set(true);
                    }
                    for (ConsumerRecord<String, byte[]> record : records) {
                        MessageConsumeResult result =
                                adapter.onMessage(record.topic(), record.key(), record.value(), headers(record));
                        if (!handleResult(record, result)) {
                            break;
                        }
                    }
                }
            } catch (org.apache.kafka.common.errors.WakeupException ignored) {
                // Expected during shutdown.
            } finally {
                consumer.close();
            }
        }

        private boolean handleResult(ConsumerRecord<String, byte[]> record, MessageConsumeResult result) {
            TopicPartition partition = new TopicPartition(record.topic(), record.partition());
            if (result.status() == MessageConsumeResult.ConsumeStatus.RETRY) {
                consumer.seek(partition, record.offset());
                sleep(result);
                return false;
            }
            if (result.status() == MessageConsumeResult.ConsumeStatus.FAILED) {
                consumer.seek(partition, record.offset());
                sleep(result);
                return false;
            }
            consumer.commitSync(Collections.singletonMap(partition, new OffsetAndMetadata(record.offset() + 1)));
            return true;
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

        private Map<String, String> headers(ConsumerRecord<String, byte[]> record) {
            Map<String, String> headers = new HashMap<>();
            for (Header header : record.headers()) {
                headers.put(header.key(), new String(header.value(), java.nio.charset.StandardCharsets.UTF_8));
            }
            return headers;
        }
    }
}
