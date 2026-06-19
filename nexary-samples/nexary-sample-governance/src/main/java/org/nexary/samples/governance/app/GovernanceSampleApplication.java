package org.nexary.samples.governance.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the Nexary governance sample application. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.governance")
public class GovernanceSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(GovernanceSampleApplication.class, args);
    }
}
