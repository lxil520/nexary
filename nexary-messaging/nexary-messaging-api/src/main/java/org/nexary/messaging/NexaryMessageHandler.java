package org.nexary.messaging;

/**
 * Business-facing message handler.
 *
 * @param <T> message payload type
 */
public interface NexaryMessageHandler<T> {
    /** Handles a deserialized business message. */
    void handleMessage(T message);
}
