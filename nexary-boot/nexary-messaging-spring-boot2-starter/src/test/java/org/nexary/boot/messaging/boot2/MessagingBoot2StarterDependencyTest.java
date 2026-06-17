package org.nexary.boot.messaging.boot2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.nexary.messaging.MessagePublisher;
import org.nexary.messaging.redis.boot2.RedisBoot2MessageQueue;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

class MessagingBoot2StarterDependencyTest {
    @Test
    void starterExposesMessagingApiAndBoot2RedisProviderLine() throws Exception {
        assertThat(MessagePublisher.class.getName()).isEqualTo("org.nexary.messaging.MessagePublisher");
        assertThat(RedisBoot2MessageQueue.class.getName()).isEqualTo(
                "org.nexary.messaging.redis.boot2.RedisBoot2MessageQueue");

        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")) {
            assertThat(stream).isNotNull();
            String factories = read(stream);
            assertThat(factories).contains(EnableAutoConfiguration.class.getName());
            assertThat(factories).contains("org.nexary.messaging.redis.boot2.RedisBoot2MessagingAutoConfiguration");
            assertThat(factories).contains("org.nexary.messaging.NexaryMessageListenerAutoConfiguration");
        }
    }

    private static String read(InputStream stream) {
        Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}
