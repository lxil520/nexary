package org.nexary.samples.governance.platform;

import org.nexary.governance.platform.server.GovernancePlatformConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/** Starts the Nexary governance platform sample. */
@SpringBootApplication
@Import(GovernancePlatformConfiguration.class)
public class GovernancePlatformSampleApplication {
    /** Application entry point. */
    public static void main(String[] args) {
        SpringApplication.run(GovernancePlatformSampleApplication.class, args);
    }
}
