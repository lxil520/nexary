package org.nexary.boot.messaging.boot4;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.nexary.messaging.MessagePublisher;

class MessagingBoot4StarterDependencyTest {
    @Test
    void starterExposesMessagingApiWithoutAggregatingProviderStacks() {
        assertThat(MessagePublisher.class.getName()).isEqualTo("org.nexary.messaging.MessagePublisher");
    }

    @Test
    void starterRegistersBoot4AutoConfigurationImports() {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(stream).isNotNull();
            String imports = read(stream);
            assertThat(imports).contains("org.nexary.messaging.NexaryMessageListenerAutoConfiguration");
            assertThat(imports).doesNotContain("org.nexary.messaging.disruptor");
            assertThat(imports).doesNotContain("org.nexary.messaging.kafka");
            assertThat(imports).doesNotContain("org.nexary.messaging.redis");
            assertThat(imports).doesNotContain("org.nexary.messaging.rocketmq");
        } catch (java.io.IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private static String read(InputStream stream) {
        Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
    }
}
