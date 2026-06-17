package org.nexary.samples.job.spi.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** SPI provider sample that introduces only the local scheduler provider. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.job")
public class JobSpiSchedulerSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobSpiSchedulerSampleApplication.class, args);
    }
}
