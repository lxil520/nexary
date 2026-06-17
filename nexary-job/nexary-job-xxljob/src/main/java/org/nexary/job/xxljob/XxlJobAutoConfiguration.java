package org.nexary.job.xxljob;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.execution.InMemoryJobExecutionStore;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto-configuration for XXL-JOB bridge support. */
@AutoConfiguration
@ConditionalOnProperty(prefix = "nexary.job", name = "provider", havingValue = "xxljob")
@EnableConfigurationProperties(XxlJobProperties.class)
public class XxlJobAutoConfiguration {
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
            XxlJobProperties properties,
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
                listeners.orderedStream().toList(),
                nexaryJobExecutionExecutor,
                executionStore,
                observationPublisher(observationPublisher, observationListeners),
                "xxljob");
    }

    @Bean
    @ConditionalOnMissingBean(XxlJobBridge.class)
    public XxlJobBridge xxlJobBridge(
            ObjectProvider<NexaryJob> jobs,
            JobExecutionRunner executionRunner,
            XxlJobProperties properties,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<NexaryObservationListener> observationListeners) {
        return new XxlJobBridge(
                jobs.orderedStream().toList(),
                executionRunner,
                properties,
                observationPublisher(observationPublisher, observationListeners));
    }

    @Bean
    @ConditionalOnMissingBean(NexaryJobOperations.class)
    public NexaryJobOperations nexaryJobOperations(XxlJobBridge bridge) {
        return new XxlJobOperations(bridge);
    }

    private NexaryObservationPublisher observationPublisher(
            ObjectProvider<NexaryObservationPublisher> publisher,
            ObjectProvider<NexaryObservationListener> listeners) {
        return publisher.getIfAvailable(() -> NexaryObservationPublisher.fanOut(listeners.orderedStream().toList()));
    }
}
