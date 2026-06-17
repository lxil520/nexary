package org.nexary.messaging;

import java.util.Objects;
import org.nexary.core.retry.RetrySignal;

/** Result of a message publish attempt. */
public final class MessagePublishResult {
    private final PublishStatus status;
    private final String providerMessageId;
    private final RetrySignal retrySignal;
    private final String detail;

    public MessagePublishResult(PublishStatus status, String providerMessageId, RetrySignal retrySignal, String detail) {
        this.status = status;
        this.providerMessageId = providerMessageId;
        this.retrySignal = retrySignal;
        this.detail = detail;
    }

    /** Creates a successful publish result. */
    public static MessagePublishResult success(String providerMessageId) {
        return new MessagePublishResult(PublishStatus.SUCCESS, providerMessageId, null, "");
    }

    /** Creates a failed publish result. */
    public static MessagePublishResult failed(String detail, RetrySignal retrySignal) {
        return new MessagePublishResult(PublishStatus.FAILED, null, retrySignal, detail);
    }

    /** Publish status. */
    public enum PublishStatus { SUCCESS, FAILED }

    /** Publish status. */
    public PublishStatus status() {
        return status;
    }

    /** Provider message id, when available. */
    public String providerMessageId() {
        return providerMessageId;
    }

    /** Retry signal for failed publish attempts. */
    public RetrySignal retrySignal() {
        return retrySignal;
    }

    /** Human-readable result detail. */
    public String detail() {
        return detail;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MessagePublishResult)) {
            return false;
        }
        MessagePublishResult that = (MessagePublishResult) other;
        return status == that.status
                && Objects.equals(providerMessageId, that.providerMessageId)
                && Objects.equals(retrySignal, that.retrySignal)
                && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, providerMessageId, retrySignal, detail);
    }

    @Override
    public String toString() {
        return "MessagePublishResult[status=" + status
                + ", providerMessageId=" + providerMessageId
                + ", retrySignal=" + retrySignal
                + ", detail=" + detail
                + "]";
    }
}
