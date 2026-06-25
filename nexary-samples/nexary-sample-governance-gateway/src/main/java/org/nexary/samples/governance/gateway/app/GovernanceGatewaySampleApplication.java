package org.nexary.samples.governance.gateway.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the Spring Cloud Gateway sample for request cancellation propagation. */
@SpringBootApplication
public class GovernanceGatewaySampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(GovernanceGatewaySampleApplication.class, args);
    }
}
