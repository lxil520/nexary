package org.nexary.messaging;

/** Handles a consumed message. */
@FunctionalInterface
public interface MessageConsumer<T> {
    /** Processes a message envelope. */
    void handle(MessageEnvelope<T> envelope) throws Exception;
}
