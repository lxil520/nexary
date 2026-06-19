package org.nexary.samples.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.nexary.samples.messaging.api.AppErrorLogController;
import org.nexary.samples.messaging.app.MessagingSampleApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = MessagingSampleApplication.class,
        properties = {
                "spring.profiles.active=activemq-classic",
                "nexary.messaging.activemq-classic.broker-url=vm://nexary-sample-activemq?broker.persistent=false&broker.useJmx=false",
                "nexary.messaging.activemq-classic.receive-timeout=100ms",
                "nexary.messaging.activemq-classic.retry-initial-delay=0ms",
                "nexary.messaging.activemq-classic.retry-max-backoff=0ms"
        })
class MessagingSampleActiveMqClassicProfileTest {
    @Autowired
    private AppErrorLogController controller;

    @Test
    void activemqClassicProfilePublishesConsumesAndDeduplicates() throws Exception {
        String messageId = "message-" + UUID.randomUUID();
        var first = controller.publish(new AppErrorLogController.PublishRequest(
                "demo-app",
                messageId,
                "ERROR",
                "sample error"));
        assertThat(first.result().status().name()).isEqualTo("SUCCESS");

        waitForConsumedSize(1);
        assertThat(controller.messages().published()).hasSize(1);
        assertThat(controller.messages().consumed()).hasSize(1);
    }

    private void waitForConsumedSize(int expectedSize) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (controller.messages().consumed().size() == expectedSize) {
                return;
            }
            Thread.sleep(50L);
        }
    }
}
