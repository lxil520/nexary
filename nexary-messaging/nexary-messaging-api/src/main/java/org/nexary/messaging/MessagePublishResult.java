package org.nexary.messaging;

import org.nexary.core.retry.RetrySignal;

/** Result of a message publish attempt. */
public record MessagePublishResult(PublishStatus status, String providerMessageId, RetrySignal retrySignal, String detail) {
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
}
