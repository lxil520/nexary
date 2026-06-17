package org.nexary.messaging;

import java.util.concurrent.CompletionStage;

/** Publishes messages through a provider-specific adapter. */
public interface MessagePublisher {
    /** Publishes a message. */
    CompletionStage<MessagePublishResult> publish(MessageEnvelope<?> envelope);
}
