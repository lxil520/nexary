package org.nexary.messaging;

/** Publishes terminal consume failures without exposing provider-native DLQ APIs. */
@FunctionalInterface
public interface MessageDeadLetterPublisher {
    /** Records a terminal failure after retry policy exhaustion. */
    void publish(MessageDeadLetterRecord record);

    /** Creates an in-memory terminal failure publisher for local profiles and tests. */
    static InMemoryMessageDeadLetterPublisher inMemory() {
        return new InMemoryMessageDeadLetterPublisher();
    }
}
