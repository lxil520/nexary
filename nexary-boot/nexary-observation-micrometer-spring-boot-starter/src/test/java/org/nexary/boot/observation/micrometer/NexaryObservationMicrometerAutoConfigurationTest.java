package org.nexary.boot.observation.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NexaryObservationMicrometerAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(NexaryObservationMicrometerAutoConfiguration.class));

    @Test
    void createsListenerWhenEnabledAndRegistryExists() {
        contextRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(NexaryObservationMicrometerProperties.class);
                    assertThat(context).hasSingleBean(NexaryMicrometerObservationListener.class);
                    assertThat(context).hasSingleBean(NexaryObservationPublisher.class);
                    assertThat(context).hasBean("nexaryMicrometerObservationListener");
                    assertThat(context.getBean(NexaryObservationListener.class))
                            .isInstanceOf(NexaryMicrometerObservationListener.class);
                });
    }

    @Test
    void disabledBridgeDoesNotCreateListener() {
        contextRunner
                .withPropertyValues("nexary.observation.micrometer.enabled=false")
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(NexaryObservationMicrometerProperties.class);
                    assertThat(context).doesNotHaveBean(NexaryMicrometerObservationListener.class);
                    assertThat(context).doesNotHaveBean(NexaryObservationListener.class);
                    assertThat(context).doesNotHaveBean(NexaryObservationPublisher.class);
                });
    }

    @Test
    void noRegistryDoesNotCreateListener() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(NexaryObservationMicrometerProperties.class);
            assertThat(context).doesNotHaveBean(NexaryMicrometerObservationListener.class);
            assertThat(context).doesNotHaveBean(NexaryObservationListener.class);
            assertThat(context).hasSingleBean(NexaryObservationPublisher.class);
        });
    }
}
