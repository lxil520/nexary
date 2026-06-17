package org.nexary.messaging;

/** Claim returned by a duplicate consumption protection store. */
public interface MessageDeduplicationClaim extends AutoCloseable {
    /** Claimed message id. */
    String messageId();

    /** Marks the message as fully consumed. */
    void complete();

    /** Releases an incomplete claim so a broker retry can process it again. */
    @Override
    void close();
}
