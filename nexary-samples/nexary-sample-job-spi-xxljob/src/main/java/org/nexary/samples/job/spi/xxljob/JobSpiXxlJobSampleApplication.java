package org.nexary.samples.job.spi.xxljob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** SPI provider sample that introduces only the XXL-JOB bridge provider. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.job")
public class JobSpiXxlJobSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobSpiXxlJobSampleApplication.class, args);
    }
}
