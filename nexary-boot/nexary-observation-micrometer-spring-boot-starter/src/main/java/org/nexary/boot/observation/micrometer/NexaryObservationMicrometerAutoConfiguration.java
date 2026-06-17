package org.nexary.boot.observation.micrometer;

import io.micrometer.core.instrument.MeterRegistry;
import org.nexary.core.observation.NexaryObservationListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for the Nexary Micrometer observation bridge. */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(NexaryObservationMicrometerProperties.class)
public class NexaryObservationMicrometerAutoConfiguration {
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(NexaryMicrometerObservationListener.class)
    @ConditionalOnProperty(
            prefix = "nexary.observation.micrometer",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public NexaryObservationListener nexaryMicrometerObservationListener(
            MeterRegistry meterRegistry,
            NexaryObservationMicrometerProperties properties) {
        return new NexaryMicrometerObservationListener(meterRegistry, properties);
    }
}
