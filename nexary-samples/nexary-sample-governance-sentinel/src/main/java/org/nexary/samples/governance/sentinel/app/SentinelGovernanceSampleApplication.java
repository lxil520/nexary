package org.nexary.samples.governance.sentinel.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boot 3 sample for the Sentinel-backed Nexary governance runtime. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.governance.sentinel")
public class SentinelGovernanceSampleApplication {
    /** Starts the Sentinel governance sample. */
    public static void main(String[] args) {
        SpringApplication.run(SentinelGovernanceSampleApplication.class, args);
    }
}
