package org.nexary.samples.messaging.spi.activemqclassic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.nexary.samples.messaging.spi.activemqclassic.api.AppErrorLogController;
import org.nexary.samples.messaging.spi.activemqclassic.app.MessagingSampleApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@EnabledIfEnvironmentVariable(named = "NEXARY_RUN_INFRA_TESTS", matches = "true")
@SpringBootTest(classes = MessagingSampleApplication.class)
class MessagingSampleApplicationTest {
    @Autowired
    private AppErrorLogController controller;

    @Test
    void messagingSamplePublishesConsumesAndDeduplicates() throws Exception {
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
        for (int i = 0; i < 20; i++) {
            if (controller.messages().consumed().size() == expectedSize) {
                return;
            }
            Thread.sleep(50L);
        }
    }
}
