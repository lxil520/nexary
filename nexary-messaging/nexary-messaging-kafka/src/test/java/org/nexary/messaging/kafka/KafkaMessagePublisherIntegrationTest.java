package org.nexary.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessageRetryPolicy;

class KafkaMessagePublisherIntegrationTest {
    @Test
    void publishesHeadersToKafkaAndFeedsDedupAwareListener() throws Exception {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        String bootstrapServers = env("NEXARY_INFRA_KAFKA_BOOTSTRAP", "127.0.0.1:19092");
        String topic = "nexary.infra.kafka." + UUID.randomUUID();
        createTopic(bootstrapServers, topic);
        try (KafkaProducer<String, byte[]> producer = createProducer(bootstrapServers);
                KafkaConsumer<String, byte[]> consumer = createConsumer(bootstrapServers, topic)) {
            KafkaMessagePublisher publisher = new KafkaMessagePublisher(
                    new KafkaTemplateAdapter(producer),
                    new DefaultStringMessageSerializer());
            MessageEnvelope<String> envelope = new MessageEnvelope<>(
                    topic,
                    "42",
                    "payload",
                    Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "kafka-message-42", "tenant", "demo"),
                    null,
                    null);

            publisher.publish(envelope).toCompletableFuture().join();

            ConsumerRecord<String, byte[]> record = pollOne(consumer);
            assertThat(record).isNotNull();
            assertThat(record.key()).isEqualTo("42");
            assertThat(new String(record.value(), StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(headerValue(record, MessageEnvelope.MESSAGE_ID_HEADER)).isEqualTo("kafka-message-42");
            assertThat(headerValue(record, "tenant")).isEqualTo("demo");

            AtomicInteger calls = new AtomicInteger();
            KafkaMessageListenerAdapter<String> adapter = new KafkaMessageListenerAdapter<>(
                    new MessageConsumeExecutor(
                            Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                            Duration.ofMinutes(5),
                            java.util.List.of(),
                            new org.nexary.messaging.MessageRetryPolicy(
                                    2,
                                    Duration.ZERO,
                                    org.nexary.messaging.MessageBackoffStrategy.FIXED,
                                    1.0d,
                                    Duration.ZERO),
                            MessageDeadLetterPublisher.inMemory()),
                    new DefaultStringMessageSerializer(),
                    String.class,
                    message -> calls.incrementAndGet());

            MessageConsumeResult first = adapter.onMessage(
                    record.topic(),
                    record.key(),
                    record.value(),
                    Map.of(MessageEnvelope.MESSAGE_ID_HEADER, headerValue(record, MessageEnvelope.MESSAGE_ID_HEADER)));
            MessageConsumeResult second = adapter.onMessage(
                    record.topic(),
                    record.key(),
                    record.value(),
                    Map.of(MessageEnvelope.MESSAGE_ID_HEADER, headerValue(record, MessageEnvelope.MESSAGE_ID_HEADER)));

            assertThat(first.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.SUCCESS);
            assertThat(second.status()).isEqualTo(MessageConsumeResult.ConsumeStatus.DUPLICATE);
            assertThat(calls).hasValue(1);
        }
    }

    private static void createTopic(String bootstrapServers, String topic) throws Exception {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        try (AdminClient adminClient = AdminClient.create(properties)) {
            adminClient.createTopics(Collections.singletonList(new NewTopic(topic, 1, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    private static KafkaProducer<String, byte[]> createProducer(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return new KafkaProducer<>(properties);
    }

    private static KafkaConsumer<String, byte[]> createConsumer(String bootstrapServers, String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "nexary-infra-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(Collections.singletonList(topic));
        return consumer;
    }

    private static ConsumerRecord<String, byte[]> pollOne(KafkaConsumer<String, byte[]> consumer) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(java.time.Duration.ofSeconds(1));
            for (ConsumerRecord<String, byte[]> record : records) {
                return record;
            }
        }
        return null;
    }

    private static String headerValue(ConsumerRecord<String, byte[]> record, String headerName) {
        return new String(record.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8);
    }

    private static String env(String name, String fallback) {
        String property = System.getProperty(name);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean infraTestsEnabled() {
        return Boolean.parseBoolean(env("NEXARY_RUN_INFRA_TESTS", "false"));
    }

    static final class KafkaTemplateAdapter {
        private final KafkaProducer<String, byte[]> producer;

        private KafkaTemplateAdapter(KafkaProducer<String, byte[]> producer) {
            this.producer = producer;
        }

        public CompletableFuture<Void> send(ProducerRecord<String, byte[]> record) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            producer.send(record, (metadata, error) -> {
                if (error != null) {
                    future.completeExceptionally(error);
                    return;
                }
                future.complete(null);
            });
            return future;
        }
    }
}
