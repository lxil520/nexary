package org.nexary.samples.job.spi.powerjob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** SPI provider sample that introduces only the PowerJob bridge provider. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.job")
public class JobSpiPowerJobSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobSpiPowerJobSampleApplication.class, args);
    }
}
