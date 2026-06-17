package org.nexary.job.scheduler.boot4;

import java.util.concurrent.ScheduledFuture;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.TaskScheduler;

/** Background heartbeat for cache-backed local scheduler worker discovery. */
class LocalJobWorkerHeartbeat implements SmartLifecycle {
    private final TaskScheduler taskScheduler;
    private final LocalJobWorkerRegistry workerRegistry;
    private final LocalJobSchedulerProperties properties;
    private ScheduledFuture<?> future;
    private boolean running;

    LocalJobWorkerHeartbeat(
            TaskScheduler taskScheduler,
            LocalJobWorkerRegistry workerRegistry,
            LocalJobSchedulerProperties properties) {
        this.taskScheduler = taskScheduler;
        this.workerRegistry = workerRegistry;
        this.properties = properties;
    }

    @Override
    public void start() {
        if (running || !properties.isHeartbeatEnabled() || !workerRegistry.supportsHeartbeat()) {
            return;
        }
        workerRegistry.heartbeat();
        future = taskScheduler.scheduleAtFixedRate(workerRegistry::heartbeat, properties.getHeartbeatInterval());
        running = true;
    }

    @Override
    public void stop() {
        if (future != null) {
            future.cancel(false);
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
