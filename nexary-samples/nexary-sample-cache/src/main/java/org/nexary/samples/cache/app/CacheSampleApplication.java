package org.nexary.samples.cache.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Focused cache sample application. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.cache")
public class CacheSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(CacheSampleApplication.class, args);
    }
}
