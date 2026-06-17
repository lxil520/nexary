package org.nexary.messaging;

/** Subscription handle for a running message consumer. */
public interface MessageSubscription extends AutoCloseable {
    /** Topic being consumed. */
    String topic();

    /** Consumer group. */
    String consumerGroup();

    /** Stops the subscription. */
    @Override
    void close();
}
