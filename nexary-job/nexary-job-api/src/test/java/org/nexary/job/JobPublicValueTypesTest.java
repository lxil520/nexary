package org.nexary.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.nexary.core.context.TrafficTag;
import org.nexary.job.execution.JobConcurrencyPolicy;
import org.nexary.job.execution.JobExecutionId;
import org.nexary.job.execution.JobExecutionPolicy;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionRequest;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionTrigger;
import org.nexary.job.execution.JobMisfirePolicy;
import org.nexary.job.loadbalance.JobLoadBalanceStrategy;
import org.nexary.job.loadbalance.JobWorker;

class JobPublicValueTypesTest {
    @Test
    void jobScheduleKeepsRecordStyleAccessorsAndImmutableWorkerSnapshot() {
        ArrayList<String> workers = new ArrayList<>(Arrays.asList(" node-a ", null, "", "node-b"));
        JobExecutionPolicy policy = JobExecutionPolicy.defaults().withRetryAttempts(2);

        JobSchedule schedule = new JobSchedule(
                "sample-job",
                "0 */5 * * * *",
                false,
                0,
                JobLoadBalanceStrategy.ROUND_ROBIN,
                " node-a ",
                workers,
                policy);
        workers.add("node-c");

        assertThat(schedule.jobName()).isEqualTo("sample-job");
        assertThat(schedule.cron()).isEqualTo("0 */5 * * * *");
        assertThat(schedule.singleInstance()).isFalse();
        assertThat(schedule.shardTotal()).isEqualTo(1);
        assertThat(schedule.workerId()).isEqualTo("node-a");
        assertThat(schedule.workerIds()).containsExactly("node-a", "node-b");
        assertThat(schedule.executionPolicy()).isEqualTo(policy);
        assertThatThrownBy(() -> schedule.workerIds().add("node-c"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(schedule)
                .isEqualTo(new JobSchedule(
                        "sample-job",
                        "0 */5 * * * *",
                        false,
                        1,
                        JobLoadBalanceStrategy.ROUND_ROBIN,
                        "node-a",
                        Arrays.asList("node-a", "node-b"),
                        policy))
                .hasSameHashCodeAs(new JobSchedule(
                        "sample-job",
                        "0 */5 * * * *",
                        false,
                        1,
                        JobLoadBalanceStrategy.ROUND_ROBIN,
                        "node-a",
                        Arrays.asList("node-a", "node-b"),
                        policy));
        assertThat(schedule.toString()).startsWith("JobSchedule[");
    }

    @Test
    void jobWorkerValidatesAndKeepsValueSemantics() {
        JobWorker worker = new JobWorker("node-a", 3, 0);

        assertThat(worker.id()).isEqualTo("node-a");
        assertThat(worker.activeCount()).isEqualTo(3);
        assertThat(worker.weight()).isEqualTo(1);
        assertThat(worker).isEqualTo(new JobWorker("node-a", 3, 1));
        assertThat(worker.hashCode()).isEqualTo(new JobWorker("node-a", 3, 1).hashCode());
        assertThat(worker.toString()).isEqualTo("JobWorker[id=node-a, activeCount=3, weight=1]");
        assertThat(new JobWorker("node-a", -1, 1).activeCount()).isZero();
        assertThatThrownBy(() -> new JobWorker(" ", 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void executionPolicyNormalizesNullAndNegativeValues() {
        JobExecutionPolicy policy = new JobExecutionPolicy(
                Duration.ofMillis(-1),
                -1,
                Duration.ofMillis(-1),
                null,
                null,
                Duration.ofMillis(-1),
                null);

        assertThat(policy.timeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.retryAttempts()).isZero();
        assertThat(policy.retryBackoff()).isEqualTo(Duration.ZERO);
        assertThat(policy.concurrencyPolicy()).isEqualTo(JobConcurrencyPolicy.ALLOW);
        assertThat(policy.misfirePolicy()).isEqualTo(JobMisfirePolicy.FIRE_ONCE);
        assertThat(policy.misfireThreshold()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.lockLeaseTime()).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy).isEqualTo(JobExecutionPolicy.defaults());
        assertThat(policy.toString()).startsWith("JobExecutionPolicy[");
    }

    @Test
    void executionRequestDefaultsMissingRuntimeValues() {
        JobExecutionRequest request = new JobExecutionRequest(null, null, null, null);

        assertThat(request.executionId()).isNotNull();
        assertThat(request.trigger()).isEqualTo(JobExecutionTrigger.DIRECT);
        assertThat(request.context().jobName()).isEqualTo("unknown");
        assertThat(request.policy()).isEqualTo(JobExecutionPolicy.defaults());
        assertThat(request).isEqualTo(new JobExecutionRequest(
                request.executionId(),
                JobExecutionTrigger.DIRECT,
                request.context(),
                JobExecutionPolicy.defaults()));
    }

    @Test
    void executionIdAndRecordPreserveValueSemantics() {
        Instant now = Instant.parse("2026-06-17T08:00:00Z");
        JobExecutionId id = new JobExecutionId("execution-1");
        JobContext context = new JobContext("sample-job", now, 1, 4, TrafficTag.builder().tenant("blue").build());
        JobResult result = JobResult.success();
        JobExecutionRecord record = new JobExecutionRecord(
                id,
                JobExecutionTrigger.SCHEDULED,
                context,
                JobExecutionStatus.SUCCESS,
                result,
                2,
                now,
                now.plusMillis(10),
                Duration.ofMillis(10),
                "ok",
                "");

        assertThat(id.value()).isEqualTo("execution-1");
        assertThat(record.executionId()).isEqualTo(id);
        assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.SCHEDULED);
        assertThat(record.context()).isEqualTo(context);
        assertThat(record.status()).isEqualTo(JobExecutionStatus.SUCCESS);
        assertThat(record.result()).isEqualTo(result);
        assertThat(record.attempts()).isEqualTo(2);
        assertThat(record.startedAt()).isEqualTo(now);
        assertThat(record.endedAt()).isEqualTo(now.plusMillis(10));
        assertThat(record.duration()).isEqualTo(Duration.ofMillis(10));
        assertThat(record.message()).isEqualTo("ok");
        assertThat(record.errorMessage()).isEmpty();
        assertThat(record).isEqualTo(new JobExecutionRecord(
                new JobExecutionId("execution-1"),
                JobExecutionTrigger.SCHEDULED,
                context,
                JobExecutionStatus.SUCCESS,
                result,
                2,
                now,
                now.plusMillis(10),
                Duration.ofMillis(10),
                "ok",
                ""));
        assertThat(record.toString()).startsWith("JobExecutionRecord[");
        assertThatThrownBy(() -> new JobExecutionId(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
