package org.nexary.job.xxljob.boot2;

import java.util.Optional;
import org.nexary.job.JobResult;
import org.nexary.job.JobSchedule;
import org.nexary.job.NexaryJobOperations;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionRecord;

/** XXL-JOB bridge implementation of provider-neutral job operations. */
public class XxlJobOperations implements NexaryJobOperations {
    private final XxlJobBridge bridge;

    public XxlJobOperations(XxlJobBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String provider() {
        return "xxljob";
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
        throw new UnsupportedOperationException("XXL-JOB bridge scheduling is owned by the external platform");
    }

    @Override
    public boolean cancel(String jobName) {
        throw new UnsupportedOperationException("XXL-JOB bridge cancellation is owned by the external platform");
    }

    @Override
    public Optional<JobExecutionRecord> execution(JobExecutionId executionId) {
        return bridge.execution(executionId);
    }
}
