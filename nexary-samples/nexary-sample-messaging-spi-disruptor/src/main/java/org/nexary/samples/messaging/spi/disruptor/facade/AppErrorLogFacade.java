package org.nexary.samples.messaging.spi.disruptor.facade;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import org.nexary.messaging.MessagePublishResult;
import org.nexary.messaging.NexaryMessageProducer;
import org.nexary.samples.messaging.spi.disruptor.domain.AppErrorLogMessage;
import org.nexary.samples.messaging.spi.disruptor.domain.MessagingSampleInbox;
import org.nexary.samples.messaging.spi.disruptor.domain.MessagingSampleTopics;
import org.springframework.stereotype.Component;

/** Business facade that publishes app error logs through Nexary messaging. */
@Component
public class AppErrorLogFacade {
    private final NexaryMessageProducer messageProducer;
    private final MessagingSampleInbox inbox;

    public AppErrorLogFacade(
            NexaryMessageProducer messageProducer,
            MessagingSampleInbox inbox) {
        this.messageProducer = messageProducer;
        this.inbox = inbox;
    }

    /** Publishes an app error log. */
    public PublishResponse publish(PublishCommand command) throws ExecutionException, InterruptedException {
        String messageId = command.messageId() == null || command.messageId().isBlank()
                ? UUID.randomUUID().toString()
                : command.messageId();
        AppErrorLogMessage message = new AppErrorLogMessage(command.appId(), command.level(), command.message());

        MessagePublishResult result = messageProducer.sendMessage(
                        MessagingSampleTopics.APP_ERROR_LOG,
                        command.appId(),
                        messageId,
                        message)
                .toCompletableFuture()
                .get();

        inbox.recordPublished(messageId, command.appId(), message, result);
        return new PublishResponse(messageId, result);
    }

    /** Returns published and consumed sample events. */
    public MessagingSampleInbox.Snapshot snapshot() {
        return inbox.snapshot();
    }

    /** Publish command body. */
    public record PublishCommand(String appId, String messageId, String level, String message) {
    }

    /** Publish response body. */
    public record PublishResponse(String messageId, MessagePublishResult result) {
    }
}
