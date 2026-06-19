package org.nexary.job.powerjob;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.internal.JobCompatibilityCollections;
import org.nexary.job.execution.InMemoryJobExecutionStore;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for PowerJob bridge support. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nexary.job", name = "provider", havingValue = "powerjob")
@EnableConfigurationProperties(PowerJobProperties.class)
public class PowerJobAutoConfiguration {
    @Bean(destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "nexaryJobExecutionExecutor")
    public ExecutorService nexaryJobExecutionExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "nexary-job-exec-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newCachedThreadPool(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobExecutionStore nexaryJobExecutionStore(
            PowerJobProperties properties,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<NexaryObservationListener> observationListeners) {
        return new InMemoryJobExecutionStore(
                properties.getExecutionRecordRetention(),
                observationPublisher(observationPublisher, observationListeners));
    }

    @Bean
    @ConditionalOnMissingBean
    public JobExecutionRunner nexaryJobExecutionRunner(
            ObjectProvider<JobExecutionListener> listeners,
            ExecutorService nexaryJobExecutionExecutor,
            JobExecutionStore executionStore,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<NexaryObservationListener> observationListeners) {
        return new JobExecutionRunner(
                JobCompatibilityCollections.collectList(listeners.orderedStream()),
                nexaryJobExecutionExecutor,
                executionStore,
                observationPublisher(observationPublisher, observationListeners),
                "powerjob");
    }

    @Bean
    @ConditionalOnMissingBean(PowerJobBridge.class)
    public PowerJobBridge powerJobBridge(
            ObjectProvider<NexaryJob> jobs,
            JobExecutionRunner executionRunner,
            PowerJobProperties properties,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<NexaryObservationListener> observationListeners) {
        return new PowerJobBridge(
                JobCompatibilityCollections.collectList(jobs.orderedStream()),
                executionRunner,
                properties,
                observationPublisher(observationPublisher, observationListeners));
    }

    @Bean
    @ConditionalOnMissingBean(NexaryJobOperations.class)
    public NexaryJobOperations nexaryJobOperations(PowerJobBridge bridge) {
        return new PowerJobOperations(bridge);
    }

    private NexaryObservationPublisher observationPublisher(
            ObjectProvider<NexaryObservationPublisher> publisher,
            ObjectProvider<NexaryObservationListener> listeners) {
        return publisher.getIfAvailable(() -> NexaryObservationPublisher.fanOut(
                JobCompatibilityCollections.collectList(listeners.orderedStream())));
    }
}
