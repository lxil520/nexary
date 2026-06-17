package org.nexary.samples.messaging.spi.redis.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.nexary.messaging.MessagePublishResult;
import org.springframework.stereotype.Component;

/** Keeps published and consumed sample messages for inspection endpoints. */
@Component
public final class MessagingSampleInbox {
    private final List<PublishedMessage> publishedMessages = new ArrayList<>();
    private final List<ConsumedMessage> consumedMessages = new ArrayList<>();

    /** Records a publish attempt. */
    public synchronized void recordPublished(
            String messageId,
            String key,
            AppErrorLogMessage payload,
            MessagePublishResult result) {
        PublishedMessage message = new PublishedMessage(
                messageId,
                MessagingSampleTopics.APP_ERROR_LOG,
                key,
                payload,
                result.status().name(),
                result.providerMessageId(),
                result.detail(),
                Instant.now());
        publishedMessages.add(message);
    }

    /** Records a consumed message. */
    public synchronized void recordConsumed(AppErrorLogMessage payload) {
        consumedMessages.add(new ConsumedMessage(
                payload,
                Instant.now()));
    }

    /** Returns a snapshot for controller responses. */
    public synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(publishedMessages), List.copyOf(consumedMessages));
    }

    /** Published message view. */
    public record PublishedMessage(
            String messageId,
            String topic,
            String key,
            AppErrorLogMessage payload,
            String publishStatus,
            String providerMessageId,
            String detail,
            Instant publishedAt) {
    }

    /** Consumed message view. */
    public record ConsumedMessage(
            AppErrorLogMessage payload,
            Instant consumedAt) {
    }

    /** Combined sample snapshot. */
    public record Snapshot(List<PublishedMessage> published, List<ConsumedMessage> consumed) {
    }
}
