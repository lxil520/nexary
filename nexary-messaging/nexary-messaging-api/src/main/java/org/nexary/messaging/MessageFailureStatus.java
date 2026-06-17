package org.nexary.messaging;

/** Terminal failure reason recorded by Nexary messaging. */
public enum MessageFailureStatus {
    /** The consumer kept failing until the bounded retry policy was exhausted. */
    RETRY_EXHAUSTED,

    /** The terminal failure publisher itself failed, so the provider should not acknowledge success. */
    DEAD_LETTER_PUBLISH_FAILED
}
