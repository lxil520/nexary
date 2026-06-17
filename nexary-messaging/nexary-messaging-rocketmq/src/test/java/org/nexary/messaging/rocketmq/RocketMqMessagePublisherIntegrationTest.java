package org.nexary.messaging.rocketmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.nexary.messaging.ConcurrentMapMessageDeduplicationStore;
import org.nexary.messaging.DefaultStringMessageSerializer;
import org.nexary.messaging.MessageDeadLetterPublisher;
import org.nexary.messaging.MessageConsumeExecutor;
import org.nexary.messaging.MessageConsumeResult;
import org.nexary.messaging.MessageEnvelope;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.MessageRetryPolicy;
import org.springframework.messaging.Message;

class RocketMqMessagePublisherIntegrationTest {
    @Test
    void publishesHeadersToRocketMqAndFeedsDedupAwareListener() throws Exception {
        Assumptions.assumeTrue(infraTestsEnabled(), "infra tests disabled");
        String namesrvAddr = env("NEXARY_INFRA_ROCKETMQ_NAMESRV", "127.0.0.1:19876");
        String topic = "nexary_infra_rocketmq_" + UUID.randomUUID().toString().replace("-", "");
        DefaultMQProducer producer = createProducer(namesrvAddr);
        ensureTopic(producer, topic);
        DefaultMQPushConsumer consumer = createConsumer(namesrvAddr, topic);
        try {
            RocketMqMessagePublisher publisher = new RocketMqMessagePublisher(
                    new RocketMqTemplateAdapter(producer),
                    new DefaultStringMessageSerializer());
            AtomicReference<MessageExt> consumedMessage = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            RocketMqMessageListenerAdapter<String> adapter = new RocketMqMessageListenerAdapter<>(
                    new MessageConsumeExecutor(
                            Optional.of(new ConcurrentMapMessageDeduplicationStore()),
                            Duration.ofMinutes(5),
                            List.of(),
                            new MessageRetryPolicy(2, Duration.ZERO, org.nexary.messaging.MessageBackoffStrategy.FIXED, 1.0d, Duration.ZERO),
                            MessageDeadLetterPublisher.inMemory()),
                    new DefaultStringMessageSerializer(),
                    String.class,
                    message -> calls.incrementAndGet());
            consumer.registerMessageListener(new MessageListenerConcurrently() {
                @Override
                public ConsumeConcurrentlyStatus consumeMessage(
                        List<MessageExt> messages,
                        ConsumeConcurrentlyContext context) {
                    for (MessageExt messageExt : messages) {
                        consumedMessage.compareAndSet(null, messageExt);
                        MessageConsumeResult result = adapter.onMessage(
                                messageExt.getTopic(),
                                messageExt.getKeys(),
                                messageExt.getBody(),
                                extractHeaders(messageExt));
                        if (result.status() == MessageConsumeResult.ConsumeStatus.SUCCESS) {
                            latch.countDown();
                        }
                    }
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }
            });
            consumer.start();
            TimeUnit.SECONDS.sleep(2);

            MessageEnvelope<String> envelope = new MessageEnvelope<>(
                    topic,
                    "42",
                    "payload",
                    Map.of(MessageEnvelope.MESSAGE_ID_HEADER, "rocketmq-message-42", "tenant", "demo"),
                    null,
                    null);

            MessagePublishResult firstPublish = publisher.publish(envelope).toCompletableFuture().join();
            MessagePublishResult secondPublish = publisher.publish(envelope).toCompletableFuture().join();
            assertThat(firstPublish.status()).isEqualTo(MessagePublishResult.PublishStatus.SUCCESS);
            assertThat(secondPublish.status()).isEqualTo(MessagePublishResult.PublishStatus.SUCCESS);

            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            MessageExt messageExt = consumedMessage.get();
            assertThat(messageExt).isNotNull();
            assertThat(new String(messageExt.getBody(), StandardCharsets.UTF_8)).isEqualTo("payload");
            assertThat(messageExt.getUserProperty(MessageEnvelope.MESSAGE_ID_HEADER)).isEqualTo("rocketmq-message-42");
            assertThat(messageExt.getUserProperty("tenant")).isEqualTo("demo");
            assertThat(messageExt.getKeys()).isEqualTo("42");
            assertThat(calls).hasValue(1);
        } finally {
            consumer.shutdown();
            producer.shutdown();
        }
    }

    private static DefaultMQProducer createProducer(String namesrvAddr) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer("nexary-infra-producer");
        producer.setNamesrvAddr(namesrvAddr);
        producer.start();
        return producer;
    }

    private static void ensureTopic(DefaultMQProducer producer, String topic) throws Exception {
        producer.createTopic("TBW102", topic, 1, java.util.Collections.emptyMap());
    }

    private static DefaultMQPushConsumer createConsumer(String namesrvAddr, String topic) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("nexary-infra-consumer-" + UUID.randomUUID());
        consumer.setNamesrvAddr(namesrvAddr);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.subscribe(topic, "*");
        return consumer;
    }

    private static Map<String, String> extractHeaders(MessageExt messageExt) {
        java.util.LinkedHashMap<String, String> headers = new java.util.LinkedHashMap<>();
        if (messageExt.getProperties() != null) {
            messageExt.getProperties().forEach((key, value) -> {
                if (value != null) {
                    headers.put(key, value);
                }
            });
        }
        return headers;
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

    static final class RocketMqTemplateAdapter {
        private final DefaultMQProducer producer;

        private RocketMqTemplateAdapter(DefaultMQProducer producer) {
            this.producer = producer;
        }

        public String syncSend(String topic, Object payload) throws Exception {
            Message<byte[]> message = (Message<byte[]>) payload;
            org.apache.rocketmq.common.message.Message nativeMessage =
                    new org.apache.rocketmq.common.message.Message(topic, message.getPayload());
            Object keys = message.getHeaders().get("KEYS");
            if (keys instanceof String value && !value.isBlank()) {
                nativeMessage.setKeys(value);
            }
            message.getHeaders().forEach((name, value) -> {
                if (value != null && !"KEYS".equals(name)) {
                    nativeMessage.putUserProperty(name, String.valueOf(value));
                }
            });
            return producer.send(nativeMessage).getMsgId();
        }
    }
}
