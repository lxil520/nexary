package org.nexary.messaging;

/** Intercepts publish and consume flows. */
public interface MessageInterceptor {
    /** Called before publishing. */
    default MessageEnvelope<?> beforePublish(MessageEnvelope<?> envelope) {
        return envelope;
    }

    /** Called after publishing. */
    default void afterPublish(MessageEnvelope<?> envelope, MessagePublishResult result) {
    }

    /** Called before consuming. */
    default <T> MessageEnvelope<T> beforeConsume(MessageEnvelope<T> envelope) {
        return envelope;
    }

    /** Called after consuming. */
    default <T> void afterConsume(MessageEnvelope<T> envelope, Throwable error) {
    }
}
