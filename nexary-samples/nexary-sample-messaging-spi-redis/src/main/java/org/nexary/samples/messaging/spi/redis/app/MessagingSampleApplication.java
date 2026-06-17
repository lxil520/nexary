package org.nexary.samples.messaging.spi.redis.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Focused messaging sample application. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.messaging.spi.redis")
public class MessagingSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(MessagingSampleApplication.class, args);
    }
}
