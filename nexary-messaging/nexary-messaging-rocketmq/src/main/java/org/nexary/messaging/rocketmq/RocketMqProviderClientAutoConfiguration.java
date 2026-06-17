package org.nexary.messaging.rocketmq;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.nexary.messaging.MessageConsumeResult;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.nexary.messaging.MessageConsumer;
import org.nexary.messaging.MessageSubscriber;
import org.nexary.messaging.MessageSubscription;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

/** RocketMQ provider client auto-configuration used by starter and SPI-style provider imports. */
@AutoConfiguration(before = RocketMqMessagingAutoConfiguration.class)
@ConditionalOnProperty(prefix = "nexary.messaging", name = "provider", havingValue = "rocketmq")
@EnableConfigurationProperties(RocketMqMessagingProperties.class)
public class RocketMqProviderClientAutoConfiguration {
    @Bean(name = "rocketMQTemplate", destroyMethod = "close")
    @ConditionalOnMissingBean(name = "rocketMQTemplate")
    public Object rocketMQTemplate(RocketMqMessagingProperties properties) throws Exception {
        return new NexaryRocketMqTemplate(properties);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(MessageSubscriber.class)
    public MessageSubscriber rocketMqMessageSubscriber(
            RocketMqMessagingProperties properties,
            RocketMqMessageListenerAdapterFactory adapterFactory) {
        return new NexaryRocketMqSubscriber(properties.getNamesrvAddr(), adapterFactory);
    }

    private static final class NexaryRocketMqTemplate implements AutoCloseable {
        private final DefaultMQProducer producer;
        private final RocketMqMessagingProperties properties;

        private NexaryRocketMqTemplate(RocketMqMessagingProperties properties) throws Exception {
            this.properties = properties;
            this.producer = new DefaultMQProducer(properties.getProducerGroup());
            this.producer.setNamesrvAddr(properties.getNamesrvAddr());
            this.producer.start();
        }

        public SendResult syncSend(String destination, Object messageObject) throws Exception {
            if (!(messageObject instanceof Message<?> springMessage)) {
                throw new IllegalArgumentException("expected Spring message");
            }
            if (properties.isAutoCreateTopic()) {
                producer.createTopic(
                        "TBW102",
                        destination,
                        properties.getTopicQueueNums(),
                        java.util.Collections.emptyMap());
            }
            org.apache.rocketmq.common.message.Message rocketMessage =
                    new org.apache.rocketmq.common.message.Message(destination, payload(springMessage.getPayload()));
            springMessage.getHeaders().forEach((name, value) -> {
                if (value != null && !"KEYS".equals(name)) {
                    rocketMessage.putUserProperty(name, value.toString());
                }
            });
            Object keys = springMessage.getHeaders().get("KEYS");
            if (keys != null) {
                rocketMessage.setKeys(keys.toString());
            }
            return producer.send(rocketMessage);
        }

        @Override
        public void close() {
            producer.shutdown();
        }

        private byte[] payload(Object payload) {
            if (payload instanceof byte[] bytes) {
                return bytes;
            }
            return payload == null ? new byte[0] : payload.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class NexaryRocketMqSubscriber implements MessageSubscriber, AutoCloseable {
        private final String namesrvAddr;
        private final RocketMqMessageListenerAdapterFactory adapterFactory;

        private NexaryRocketMqSubscriber(String namesrvAddr, RocketMqMessageListenerAdapterFactory adapterFactory) {
            this.namesrvAddr = namesrvAddr;
            this.adapterFactory = adapterFactory;
        }

        @Override
        public <T> MessageSubscription subscribe(
                String topic,
                String consumerGroup,
                Class<T> payloadType,
                MessageConsumer<T> consumer) {
            RocketMqMessageListenerAdapter<T> adapter = adapterFactory.create(consumerGroup, payloadType, consumer);
            NexaryRocketMqSubscription<T> subscription =
                    new NexaryRocketMqSubscription<>(namesrvAddr, topic, consumerGroup, adapter);
            subscription.start();
            return subscription;
        }

        @Override
        public void close() {
        }
    }

    private static final class NexaryRocketMqSubscription<T> implements MessageSubscription {
        private final String namesrvAddr;
        private final String topic;
        private final String consumerGroup;
        private final RocketMqMessageListenerAdapter<T> adapter;
        private DefaultMQPushConsumer consumer;

        private NexaryRocketMqSubscription(
                String namesrvAddr,
                String topic,
                String consumerGroup,
                RocketMqMessageListenerAdapter<T> adapter) {
            this.namesrvAddr = namesrvAddr;
            this.topic = topic;
            this.consumerGroup = consumerGroup;
            this.adapter = adapter;
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
            if (consumer != null) {
                consumer.shutdown();
            }
        }

        private void start() {
            try {
                this.consumer = new DefaultMQPushConsumer(consumerGroup);
                this.consumer.setNamesrvAddr(namesrvAddr);
                this.consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
                this.consumer.subscribe(topic, "*");
                this.consumer.registerMessageListener(new Listener());
                this.consumer.start();
            } catch (Exception ex) {
                throw new IllegalStateException("failed to start RocketMQ message subscriber", ex);
            }
        }

        private final class Listener implements MessageListenerConcurrently {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(
                    List<MessageExt> messages,
                    ConsumeConcurrentlyContext context) {
                for (MessageExt message : messages) {
                    MessageConsumeResult result = adapter.onMessage(
                            message.getTopic(),
                            message.getKeys(),
                            message.getBody(),
                            headers(message));
                    if (result.status() == MessageConsumeResult.ConsumeStatus.RETRY
                            || result.status() == MessageConsumeResult.ConsumeStatus.FAILED) {
                        context.setDelayLevelWhenNextConsume(delayLevel(result));
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }

            private int delayLevel(MessageConsumeResult result) {
                Duration backoff = result.retrySignal() == null ? Duration.ZERO : result.retrySignal().backoff();
                long seconds = Math.max(0, backoff.toSeconds());
                if (seconds <= 1) {
                    return 1;
                }
                if (seconds <= 5) {
                    return 2;
                }
                if (seconds <= 10) {
                    return 3;
                }
                if (seconds <= 30) {
                    return 4;
                }
                return 5;
            }
        }

        private Map<String, String> headers(MessageExt message) {
            Map<String, String> headers = new HashMap<>();
            if (message.getProperties() != null) {
                message.getProperties().forEach((key, value) -> {
                    if (value != null) {
                        headers.put(key, value);
                    }
                });
            }
            return headers;
        }
    }
}
