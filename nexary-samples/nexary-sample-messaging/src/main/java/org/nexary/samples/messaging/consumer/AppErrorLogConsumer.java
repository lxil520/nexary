package org.nexary.samples.messaging.consumer;

import org.nexary.messaging.NexaryMessageHandler;
import org.nexary.messaging.NexaryMessageListener;
import org.nexary.samples.messaging.domain.AppErrorLogMessage;
import org.nexary.samples.messaging.domain.MessagingSampleInbox;
import org.nexary.samples.messaging.domain.MessagingSampleTopics;
import org.springframework.stereotype.Component;

/** Business consumer for app error logs. */
@Component
@NexaryMessageListener(
        topic = MessagingSampleTopics.APP_ERROR_LOG,
        consumerGroup = MessagingSampleTopics.APP_ERROR_LOG_GROUP,
        payloadType = AppErrorLogMessage.class)
public class AppErrorLogConsumer implements NexaryMessageHandler<AppErrorLogMessage> {
    private final MessagingSampleInbox inbox;

    public AppErrorLogConsumer(MessagingSampleInbox inbox) {
        this.inbox = inbox;
    }

    @Override
    public void handleMessage(AppErrorLogMessage message) {
        inbox.recordConsumed(message);
    }
}
