package org.nexary.samples.job.processor;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Non-web processor style job sample application. */
@SpringBootApplication
public class JobProcessorSampleApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(JobProcessorSampleApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("processor")
                .run(args);
    }
}
