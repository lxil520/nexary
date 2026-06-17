package org.nexary.samples.messaging.spi.rocketmq.domain;

/** Business topic constants used by the messaging sample. */
public final class MessagingSampleTopics {
    /** App error log topic used by the sample producer and consumer. */
    public static final String APP_ERROR_LOG = "sample_messaging_app_error_log";

    /** Consumer group for the app error log sample. */
    public static final String APP_ERROR_LOG_GROUP = "sample-messaging-app-error-log-consumer";

    private MessagingSampleTopics() {
    }
}
