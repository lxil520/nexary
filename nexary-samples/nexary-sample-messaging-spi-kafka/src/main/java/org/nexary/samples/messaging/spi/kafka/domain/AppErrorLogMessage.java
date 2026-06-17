package org.nexary.samples.messaging.spi.kafka.domain;

/** App error log message used by the sample business flow. */
public record AppErrorLogMessage(String appId, String level, String message) {
}
