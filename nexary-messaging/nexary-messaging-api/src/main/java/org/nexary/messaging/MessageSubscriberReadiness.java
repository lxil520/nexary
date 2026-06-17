package org.nexary.messaging;

/**
 * Optional readiness contract for subscribers that need an asynchronous broker assignment phase before messages can be
 * consumed.
 */
public interface MessageSubscriberReadiness {
    /** Returns whether the subscriber has at least one active subscription ready to consume messages. */
    boolean isReady();
}
