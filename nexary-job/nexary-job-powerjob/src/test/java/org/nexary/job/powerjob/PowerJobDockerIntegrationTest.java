package org.nexary.job.powerjob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.nexary.core.observation.NexaryObservationPublisher;
import org.nexary.job.JobContext;
import org.nexary.job.JobResult;
import org.nexary.job.NexaryJob;
import org.nexary.job.execution.InMemoryJobExecutionStore;
import org.nexary.job.execution.JobExecutionRecord;
import org.nexary.job.execution.JobExecutionRunner;
import org.nexary.job.execution.JobExecutionStatus;
import org.nexary.job.execution.JobExecutionStore;
import org.nexary.job.execution.JobExecutionTrigger;

class PowerJobDockerIntegrationTest {
    @Test
    void bridgeExecutionIsValidatedWhenPowerJobDockerPlatformIsReachable() {
        assumeTrue(runInfraTests(), "set NEXARY_RUN_INFRA_TESTS=true to run PowerJob Docker integration");
        String serverHost = property("NEXARY_INFRA_POWERJOB_SERVER_HOST", "127.0.0.1");
        int serverPort = integerProperty("NEXARY_INFRA_POWERJOB_SERVER_PORT", 17700);
        String workerHost = property("NEXARY_INFRA_POWERJOB_WORKER_HOST", "127.0.0.1");
        int workerPort = integerProperty("NEXARY_INFRA_POWERJOB_WORKER_PORT", 27777);

        assertReachable(serverHost, serverPort, "PowerJob server");
        assertReachable(workerHost, workerPort, "PowerJob worker sample");

        JobExecutionStore store = new InMemoryJobExecutionStore(Duration.ofMinutes(5));
        NexaryJob job = new NexaryJob() {
            @Override
            public String name() {
                return "powerjob-docker-bridge-job";
            }

            @Override
            public JobResult execute(JobContext context) {
                assertThat(context.shardIndex()).isEqualTo(2);
                assertThat(context.shardTotal()).isEqualTo(5);
                return new JobResult(JobResult.JobStatus.SUCCESS, "powerjob docker bridge executed");
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "nexary-powerjob-docker-it");
            thread.setDaemon(true);
            return thread;
        });
        try {
            JobExecutionRunner runner = new JobExecutionRunner(
                    Collections.emptyList(),
                    executor,
                    store,
                    NexaryObservationPublisher.noop(),
                    "powerjob");
            PowerJobBridge bridge = new PowerJobBridge(Collections.singletonList(job), runner, new PowerJobProperties());

            JobExecutionRecord record = bridge.triggerExecution(new PowerJobBridgeRequest(
                    "powerjob-docker-bridge-job",
                    2,
                    5,
                    "docker-worker-samples",
                    "docker-task",
                    "docker-bridge-check"));

            assertThat(record.trigger()).isEqualTo(JobExecutionTrigger.BRIDGE);
            assertThat(record.status()).isEqualTo(JobExecutionStatus.SUCCESS);
            assertThat(record.result().message()).contains("powerjob docker bridge executed");
            assertThat(record.providerMetadata())
                    .containsEntry("provider", "powerjob")
                    .containsEntry("instance_id", "docker-worker-samples")
                    .containsEntry("task_id", "docker-task")
                    .containsEntry("task_name", "docker-bridge-check")
                    .containsEntry("shard_index", "2")
                    .containsEntry("shard_total", "5");
            assertThat(store.find(record.executionId())).contains(record);
            assertThat(bridge.execution(record.executionId())).contains(record);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertReachable(String host, int port, String serviceName) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2_000);
        } catch (IOException ex) {
            throw new AssertionError(serviceName + " is not reachable at " + host + ":" + port, ex);
        }
    }

    private static boolean runInfraTests() {
        return "true".equalsIgnoreCase(property("NEXARY_RUN_INFRA_TESTS", "false"));
    }

    private static int integerProperty(String name, int fallback) {
        return Integer.parseInt(property(name, String.valueOf(fallback)));
    }

    private static String property(String name, String fallback) {
        return Optional.ofNullable(System.getProperty(name)).orElseGet(() -> Optional.ofNullable(System.getenv(name)).orElse(fallback));
    }
}
