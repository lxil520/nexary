package org.nexary.samples.job.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/** Focused job sample application. */
@SpringBootApplication(scanBasePackages = "org.nexary.samples.job")
@ComponentScan(basePackages = "org.nexary.samples.job", excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "org\\.nexary\\.samples\\.job\\.processor\\..*"))
public class JobSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobSampleApplication.class, args);
    }
}
