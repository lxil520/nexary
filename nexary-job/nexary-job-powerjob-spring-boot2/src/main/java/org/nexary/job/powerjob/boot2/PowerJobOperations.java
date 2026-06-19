package org.nexary.job.powerjob.boot2;

import java.util.Optional;
import org.nexary.job.JobResult;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;

/** PowerJob bridge implementation of provider-neutral job operations. */
public class PowerJobOperations implements NexaryJobOperations {
    private final PowerJobBridge bridge;

    public PowerJobOperations(PowerJobBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String provider() {
        return "powerjob";
    }

    @Override
    public boolean supportsScheduling() {
        return false;
    }

    @Override
    public JobResult trigger(String jobName, int shardIndex, int shardTotal) {
        return bridge.trigger(jobName, shardIndex, shardTotal);
    }

    @Override
    public JobExecutionRecord triggerExecution(String jobName, int shardIndex, int shardTotal) {
        return bridge.triggerExecution(jobName, shardIndex, shardTotal);
    }

    @Override
    public void schedule(JobSchedule schedule) {
        throw new UnsupportedOperationException("PowerJob bridge scheduling is owned by the external platform");
    }

    @Override
    public boolean cancel(String jobName) {
        throw new UnsupportedOperationException("PowerJob bridge cancellation is owned by the external platform");
    }

    @Override
    public Optional<JobExecutionRecord> execution(JobExecutionId executionId) {
        return bridge.execution(executionId);
    }
}
