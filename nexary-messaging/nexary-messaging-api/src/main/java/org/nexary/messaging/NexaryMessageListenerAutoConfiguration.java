package org.nexary.messaging;

import java.util.ArrayList;
import java.util.List;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.env.Environment;

/** Auto-registers business consumers annotated with {@link NexaryMessageListener}. */
@AutoConfiguration
public class NexaryMessageListenerAutoConfiguration implements SmartInitializingSingleton, SmartLifecycle {
    private final ObjectProvider<MessageSubscriber> subscriberProvider;
    private final ObjectProvider<MessageConsumer> consumers;
    private final ObjectProvider<NexaryMessageHandler> handlers;
    private final Environment environment;
    private final List<MessageSubscription> subscriptions = new ArrayList<>();
    private volatile boolean running;

    @Bean
    @ConditionalOnMissingBean(NexaryMessageProducer.class)
    public NexaryMessageProducer nexaryMessageProducer(ObjectProvider<MessagePublisher> publisherProvider) {
        return new NexaryMessageProducer(publisherProvider);
    }

    @Bean
    @ConditionalOnMissingBean(MessageDeadLetterPublisher.class)
    public MessageDeadLetterPublisher messageDeadLetterPublisher() {
        return MessageDeadLetterPublisher.inMemory();
    }

    @Autowired
    public NexaryMessageListenerAutoConfiguration(
            ObjectProvider<MessageSubscriber> subscriberProvider,
            ObjectProvider<MessageConsumer> consumers,
            ObjectProvider<NexaryMessageHandler> handlers,
            Environment environment) {
        this.subscriberProvider = subscriberProvider;
        this.consumers = consumers;
        this.handlers = handlers;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        subscribeAvailableConsumers();
    }

    private void subscribeAvailableConsumers() {
        if (!subscriptions.isEmpty()) {
            return;
        }
        MessageSubscriber subscriber = subscriberProvider.getIfAvailable();
        if (subscriber == null) {
            return;
        }
        consumers.orderedStream().forEach(consumer -> subscribeIfAnnotated(subscriber, consumer));
        handlers.orderedStream().forEach(handler -> subscribeHandlerIfAnnotated(subscriber, handler));
        running = true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void subscribeIfAnnotated(MessageSubscriber subscriber, MessageConsumer consumer) {
        NexaryMessageListener listener = AopUtils.getTargetClass(consumer).getAnnotation(NexaryMessageListener.class);
        if (listener == null) {
            return;
        }
        String topic = environment.resolvePlaceholders(listener.topic());
        String consumerGroup = environment.resolvePlaceholders(listener.consumerGroup());
        subscriptions.add(subscriber.subscribe(topic, consumerGroup, (Class) listener.payloadType(), consumer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void subscribeHandlerIfAnnotated(MessageSubscriber subscriber, NexaryMessageHandler handler) {
        NexaryMessageListener listener = AopUtils.getTargetClass(handler).getAnnotation(NexaryMessageListener.class);
        if (listener == null) {
            return;
        }
        String topic = environment.resolvePlaceholders(listener.topic());
        String consumerGroup = environment.resolvePlaceholders(listener.consumerGroup());
        MessageConsumer consumer = envelope -> handler.handleMessage(envelope.payload());
        subscriptions.add(subscriber.subscribe(topic, consumerGroup, (Class) listener.payloadType(), consumer));
    }

    @Override
    public void start() {
        subscribeAvailableConsumers();
        running = true;
    }

    @Override
    public void stop() {
        subscriptions.forEach(MessageSubscription::close);
        subscriptions.clear();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
