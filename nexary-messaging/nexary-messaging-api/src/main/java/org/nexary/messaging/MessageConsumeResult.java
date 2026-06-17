package org.nexary.messaging;

import java.util.Objects;
import org.nexary.core.retry.RetrySignal;

/** Result of a message consume attempt. */
public final class MessageConsumeResult {
    private final ConsumeStatus status;
    private final RetrySignal retrySignal;
    private final String detail;
    private final MessageDeadLetterRecord deadLetterRecord;

    public MessageConsumeResult(
            ConsumeStatus status,
            RetrySignal retrySignal,
            String detail,
            MessageDeadLetterRecord deadLetterRecord) {
        this.status = status;
        this.retrySignal = retrySignal;
        this.detail = detail;
        this.deadLetterRecord = deadLetterRecord;
    }

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

    /** Consume status. */
    public ConsumeStatus status() {
        return status;
    }

    /** Retry signal returned by retryable or failed consume outcomes. */
    public RetrySignal retrySignal() {
        return retrySignal;
    }

    /** Human-readable outcome detail. */
    public String detail() {
        return detail;
    }

    /** Terminal dead-letter record when retry exhaustion occurred. */
    public MessageDeadLetterRecord deadLetterRecord() {
        return deadLetterRecord;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessageConsumeResult)) {
            return false;
        }
        MessageConsumeResult that = (MessageConsumeResult) other;
        return status == that.status
                && Objects.equals(retrySignal, that.retrySignal)
                && Objects.equals(detail, that.detail)
                && Objects.equals(deadLetterRecord, that.deadLetterRecord);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, retrySignal, detail, deadLetterRecord);
    }

    @Override
    public String toString() {
        return "MessageConsumeResult[status=" + status
                + ", retrySignal=" + retrySignal
                + ", detail=" + detail
                + ", deadLetterRecord=" + deadLetterRecord
                + "]";
    }
}
