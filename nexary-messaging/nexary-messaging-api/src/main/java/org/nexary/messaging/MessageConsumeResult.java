package org.nexary.messaging;

import org.nexary.core.retry.RetrySignal;

/** Result of a message consume attempt. */
public record MessageConsumeResult(
        ConsumeStatus status,
        RetrySignal retrySignal,
        String detail,
        MessageDeadLetterRecord deadLetterRecord) {
    /** Creates a successful consume result. */
    public static MessageConsumeResult success() {
        return new MessageConsumeResult(ConsumeStatus.SUCCESS, null, "", null);
    }

    /** Creates a result for a duplicate message that has already been accepted. */
    public static MessageConsumeResult duplicate(String detail) {
        return new MessageConsumeResult(ConsumeStatus.DUPLICATE, null, detail, null);
    }

    /** Creates a retryable consume result. */
    public static MessageConsumeResult retry(String detail, RetrySignal retrySignal) {
        return new MessageConsumeResult(ConsumeStatus.RETRY, retrySignal, detail, null);
    }

    /** Creates a terminal dead-letter result after retry exhaustion. */
    public static MessageConsumeResult deadLetter(MessageDeadLetterRecord record) {
        return new MessageConsumeResult(
                ConsumeStatus.DEAD_LETTER,
                RetrySignal.stop("message dead-lettered: " + record.messageId()),
                record.errorMessage(),
                record);
    }

    /** Creates a failed consume result that should not be retried by Nexary. */
    public static MessageConsumeResult failed(String detail) {
        return new MessageConsumeResult(ConsumeStatus.FAILED, RetrySignal.stop(detail), detail, null);
    }

    /** Consume outcome. */
    public enum ConsumeStatus {
        SUCCESS,
        DUPLICATE,
        RETRY,
        DEAD_LETTER,
        FAILED
    }
}
