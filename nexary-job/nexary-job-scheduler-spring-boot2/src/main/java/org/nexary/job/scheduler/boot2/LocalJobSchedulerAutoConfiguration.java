package org.nexary.job.scheduler.boot2;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexary.cache.CacheClient;
import org.nexary.core.observation.NexaryObservationListener;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobSchedule;
import org.nexary.job.JobExecutionListener;
import org.nexary.job.NexaryJob;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.NexaryJobScheduler;
import org.nexary.job.internal.JobCompatibilityCollections;
import org.nexary.job.execution.InMemoryJobExecutionStore;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Spring Boot auto-configuration for local job scheduling. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "nexary.job", name = "provider", havingValue = "local", matchIfMissing = true)
@EnableConfigurationProperties(LocalJobSchedulerProperties.class)
public class LocalJobSchedulerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler nexaryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("nexary-job-");
        scheduler.setPoolSize(4);
        scheduler.initialize();
        return scheduler;
    }

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
            LocalJobSchedulerProperties properties,
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
                "local");
    }

    @Bean
    @ConditionalOnMissingBean(NexaryJobScheduler.class)
    public NexaryJobScheduler nexaryJobScheduler(
            TaskScheduler taskScheduler,
            ObjectProvider<CacheClient> cacheClient,
            JobExecutionRunner executionRunner,
            LocalJobSchedulerProperties properties,
            LocalJobWorkerRegistry workerRegistry,
            ObjectProvider<NexaryObservationPublisher> observationPublisher,
            ObjectProvider<NexaryObservationListener> observationListeners) {
        return new LocalNexaryJobScheduler(
                taskScheduler,
                Optional.ofNullable(cacheClient.getIfAvailable()),
                executionRunner,
                properties,
                workerRegistry,
                observationPublisher(observationPublisher, observationListeners));
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalJobWorkerRegistry nexaryLocalJobWorkerRegistry(
            ObjectProvider<CacheClient> cacheClient,
            LocalJobSchedulerProperties properties) {
        return new CacheBackedLocalJobWorkerRegistry(Optional.ofNullable(cacheClient.getIfAvailable()), properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public LocalJobWorkerHeartbeat nexaryLocalJobWorkerHeartbeat(
            TaskScheduler taskScheduler,
            LocalJobWorkerRegistry workerRegistry,
            LocalJobSchedulerProperties properties) {
        return new LocalJobWorkerHeartbeat(taskScheduler, workerRegistry, properties);
    }

    @Bean
    @ConditionalOnMissingBean(NexaryJobOperations.class)
    public NexaryJobOperations nexaryJobOperations(
            ObjectProvider<NexaryJob> jobs,
            NexaryJobScheduler scheduler,
            JobExecutionRunner executionRunner,
            LocalJobSchedulerProperties properties) {
        return new LocalNexaryJobOperations(
                JobCompatibilityCollections.collectList(jobs.orderedStream()),
                scheduler,
                executionRunner,
                properties);
    }

    @Bean
    @ConditionalOnMissingBean(name = "nexaryConfiguredJobSchedules")
    public SmartInitializingSingleton nexaryConfiguredJobSchedules(
            NexaryJobOperations operations,
            LocalJobSchedulerProperties properties) {
        return () -> {
            if (!operations.supportsScheduling()) {
                return;
            }
            for (JobSchedule schedule : properties.toSchedules()) {
                operations.schedule(schedule);
            }
        };
    }

    private NexaryObservationPublisher observationPublisher(
            ObjectProvider<NexaryObservationPublisher> publisher,
            ObjectProvider<NexaryObservationListener> listeners) {
        return publisher.getIfAvailable(() -> NexaryObservationPublisher.fanOut(
                JobCompatibilityCollections.collectList(listeners.orderedStream())));
    }
}
