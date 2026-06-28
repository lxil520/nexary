package org.nexary.samples.governance.platform;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSignalSeverity;
import org.nexary.governance.platform.GovernanceSignalType;
import org.nexary.governance.platform.server.GovernancePlatformService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static java.util.Map.entry;

/** Runs real dependency probes against the local Docker middleware stack. */
@Component
public final class GovernancePlatformLiveProbe {
    private static final int DEFAULT_ITERATIONS = 25;
    private static final int MAX_ITERATIONS = 200;
    private static final String WORKSPACE = "cloud-phone";
    private static final String ENVIRONMENT = "prod-demo";
    private static final String ZONE = "local-docker";
    private static final String RABBIT_QUEUE = "nexary.platform.probe";

    private final StringRedisTemplate redisTemplate;
    private final GovernancePlatformService platformService;
    private final String postgresUrl;
    private final String postgresUser;
    private final String postgresPassword;
    private final String rabbitHost;
    private final int rabbitPort;
    private final String rabbitUser;
    private final String rabbitPassword;
    private final Map<String, Long> successTotals = new LinkedHashMap<>();
    private final Map<String, Long> failureTotals = new LinkedHashMap<>();
    private volatile Map<String, ProbeStats> lastStats = Map.of();

    /** Creates the live probe runner. */
    public GovernancePlatformLiveProbe(
            StringRedisTemplate redisTemplate,
            GovernancePlatformService platformService,
            @Value("${nexary.demo.postgres.url}") String postgresUrl,
            @Value("${nexary.demo.postgres.user}") String postgresUser,
            @Value("${nexary.demo.postgres.password}") String postgresPassword,
            @Value("${nexary.demo.rabbitmq.host}") String rabbitHost,
            @Value("${nexary.demo.rabbitmq.port}") int rabbitPort,
            @Value("${nexary.demo.rabbitmq.user}") String rabbitUser,
            @Value("${nexary.demo.rabbitmq.password}") String rabbitPassword) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.platformService = Objects.requireNonNull(platformService, "platformService");
        this.postgresUrl = Objects.requireNonNull(postgresUrl, "postgresUrl");
        this.postgresUser = Objects.requireNonNull(postgresUser, "postgresUser");
        this.postgresPassword = Objects.requireNonNull(postgresPassword, "postgresPassword");
        this.rabbitHost = Objects.requireNonNull(rabbitHost, "rabbitHost");
        this.rabbitPort = rabbitPort;
        this.rabbitUser = Objects.requireNonNull(rabbitUser, "rabbitUser");
        this.rabbitPassword = Objects.requireNonNull(rabbitPassword, "rabbitPassword");
    }

    /** Runs the probes and records read-only platform signals. */
    public Map<String, Object> run(int requestedIterations) {
        int iterations = clampIterations(requestedIterations);
        Instant startedAt = Instant.now();
        ProbeStats redis = probeRedis(iterations);
        ProbeStats postgres = probePostgres(iterations);
        ProbeStats rabbitmq = probeRabbitmq(iterations);

        Map<String, ProbeStats> latest = new LinkedHashMap<>();
        latest.put(redis.dependency, redis);
        latest.put(postgres.dependency, postgres);
        latest.put(rabbitmq.dependency, rabbitmq);
        lastStats = latest;

        recordProbeSignal(redis);
        recordProbeSignal(postgres);
        recordProbeSignal(rabbitmq);
        updatePrometheusCounters(latest);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("liveProbe", true);
        result.put("iterations", iterations);
        result.put("startedAt", startedAt);
        result.put("finishedAt", Instant.now());
        result.put("redis", redis.toResponse());
        result.put("postgres", postgres.toResponse());
        result.put("rabbitmq", rabbitmq.toResponse());
        return result;
    }

    /** Returns a compact Prometheus text exposition for the latest probe run. */
    public String prometheusText() {
        StringBuilder text = new StringBuilder();
        text.append("# HELP nexary_demo_probe_calls_total Cumulative local dependency probe calls\n");
        text.append("# TYPE nexary_demo_probe_calls_total counter\n");
        synchronized (this) {
            for (String dependency : List.of("redis", "postgres", "rabbitmq")) {
                text.append(metric("nexary_demo_probe_calls_total", dependency, "success", successTotals.getOrDefault(dependency, 0L)));
                text.append(metric("nexary_demo_probe_calls_total", dependency, "failure", failureTotals.getOrDefault(dependency, 0L)));
            }
        }
        text.append("# HELP nexary_demo_probe_latency_seconds Latest local dependency probe latency\n");
        text.append("# TYPE nexary_demo_probe_latency_seconds gauge\n");
        for (ProbeStats stats : lastStats.values()) {
            text.append(quantileMetric(stats.dependency, "0.95", stats.p95Ms));
            text.append(quantileMetric(stats.dependency, "0.99", stats.p99Ms));
            text.append(quantileMetric(stats.dependency, "max", stats.maxMs));
        }
        text.append("# HELP nexary_demo_jvm_memory_used_bytes Current JVM heap and non-heap memory used\n");
        text.append("# TYPE nexary_demo_jvm_memory_used_bytes gauge\n");
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        text.append("nexary_demo_jvm_memory_used_bytes{area=\"heap\"} ").append(memory.getHeapMemoryUsage().getUsed()).append('\n');
        text.append("nexary_demo_jvm_memory_used_bytes{area=\"non_heap\"} ").append(memory.getNonHeapMemoryUsage().getUsed()).append('\n');
        text.append("# HELP nexary_demo_jvm_threads_live Current live JVM thread count\n");
        text.append("# TYPE nexary_demo_jvm_threads_live gauge\n");
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        text.append("nexary_demo_jvm_threads_live ").append(threads.getThreadCount()).append('\n');
        return text.toString();
    }

    private ProbeStats probeRedis(int iterations) {
        ProbeStats stats = new ProbeStats("redis", "signaling", "signaling-cluster", "cache:signaling:live-probe",
                "probe:redis:set-get", "redis-main", "cache:redis-main:live-probe", 100);
        for (int index = 0; index < iterations; index++) {
            final int current = index;
            stats.record(() -> {
                String key = "nexary:demo:platform-probe:" + (current % 8);
                redisTemplate.opsForValue().set(key, "value-" + current, Duration.ofSeconds(60));
                String value = redisTemplate.opsForValue().get(key);
                if (value == null) {
                    throw new IllegalStateException("empty redis value");
                }
            });
            if (stats.shouldStopEarly(iterations)) {
                break;
            }
        }
        stats.finish(iterations);
        return stats;
    }

    private ProbeStats probePostgres(int iterations) {
        ProbeStats stats = new ProbeStats("postgres", "open-api", "open-api-cluster", "jdbc:open-api:live-probe",
                "probe:postgres:write-read", "pg-primary", "jdbc:pg-primary:live-probe", 120);
        for (int index = 0; index < iterations; index++) {
            final int current = index;
            stats.record(() -> {
                try (Connection connection = DriverManager.getConnection(postgresUrl, postgresUser, postgresPassword)) {
                    connection.setNetworkTimeout(Runnable::run, 2_000);
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("create table if not exists nexary_platform_probe "
                                + "(probe_key varchar(64) primary key, probe_value varchar(64), updated_at timestamp)");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "insert into nexary_platform_probe(probe_key, probe_value, updated_at) values (?, ?, ?) "
                                    + "on conflict (probe_key) do update set probe_value = excluded.probe_value, updated_at = excluded.updated_at")) {
                        statement.setString(1, "platform-probe");
                        statement.setString(2, "value-" + current);
                        statement.setTimestamp(3, Timestamp.from(Instant.now()));
                        statement.executeUpdate();
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "select probe_value from nexary_platform_probe where probe_key = ?")) {
                        statement.setString(1, "platform-probe");
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next()) {
                                throw new IllegalStateException("empty postgres value");
                            }
                        }
                    }
                }
            });
            if (stats.shouldStopEarly(iterations)) {
                break;
            }
        }
        stats.finish(iterations);
        return stats;
    }

    private ProbeStats probeRabbitmq(int iterations) {
        ProbeStats stats = new ProbeStats("rabbitmq", "consumer", "consumer-cluster", "mq:consumer:live-probe",
                "probe:rabbitmq:publish-consume", "rabbitmq-main", "mq:rabbitmq-main:live-probe", 150);
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setPort(rabbitPort);
        factory.setUsername(rabbitUser);
        factory.setPassword(rabbitPassword);
        factory.setConnectionTimeout(2_000);
        factory.setRequestedHeartbeat(5);
        try (com.rabbitmq.client.Connection connection = factory.newConnection("nexary-platform-probe");
             Channel channel = connection.createChannel()) {
            channel.queueDeclare(RABBIT_QUEUE, false, false, true, Map.of());
            for (int index = 0; index < iterations; index++) {
                final int current = index;
                stats.record(() -> {
                    byte[] body = ("probe-" + current).getBytes(StandardCharsets.UTF_8);
                    channel.basicPublish("", RABBIT_QUEUE, null, body);
                    GetResponse response = channel.basicGet(RABBIT_QUEUE, true);
                    if (response == null || response.getBody().length == 0) {
                        throw new IllegalStateException("empty rabbitmq receive");
                    }
                });
                if (stats.shouldStopEarly(iterations)) {
                    break;
                }
            }
        } catch (Exception error) {
            while (stats.attemptedCount < iterations) {
                stats.record(() -> {
                    throw error;
                });
                if (stats.shouldStopEarly(iterations)) {
                    break;
                }
            }
        }
        stats.finish(iterations);
        return stats;
    }

    private void recordProbeSignal(ProbeStats stats) {
        platformService.recordSignal(new GovernanceSignal(
                WORKSPACE,
                ENVIRONMENT,
                stats.serviceKey,
                stats.clusterKey,
                ZONE,
                stats.resourceKey,
                stats.signalType(),
                stats.severity(),
                stats.outcome(),
                stats.durationBucket(),
                Instant.now(),
                stats.toSignalAttributes()));
    }

    private synchronized void updatePrometheusCounters(Map<String, ProbeStats> latest) {
        for (ProbeStats stats : latest.values()) {
            successTotals.merge(stats.dependency, (long) stats.successCount, Long::sum);
            failureTotals.merge(stats.dependency, (long) stats.failureCount, Long::sum);
        }
    }

    private String metric(String metricName, String dependency, String result, long value) {
        return metricName + "{dependency=\"" + dependency + "\",result=\"" + result + "\"} " + value + "\n";
    }

    private String quantileMetric(String dependency, String quantile, long milliseconds) {
        return "nexary_demo_probe_latency_seconds{dependency=\"" + dependency + "\",quantile=\"" + quantile + "\"} "
                + ProbeStats.decimal(milliseconds / 1_000.0) + "\n";
    }

    private int clampIterations(int requestedIterations) {
        if (requestedIterations <= 0) {
            return DEFAULT_ITERATIONS;
        }
        return Math.min(requestedIterations, MAX_ITERATIONS);
    }

    @FunctionalInterface
    private interface ProbeOperation {
        void run() throws Exception;
    }

    private static final class ProbeStats {
        private final String dependency;
        private final String serviceKey;
        private final String clusterKey;
        private final String resourceKey;
        private final String endpoint;
        private final String targetService;
        private final String targetResource;
        private final long warningP95Ms;
        private final List<Long> latenciesNanos = new ArrayList<>();

        private int attemptedCount;
        private int successCount;
        private int failureCount;
        private String lastFailure = "NONE";
        private long minMs;
        private long avgMs;
        private long p95Ms;
        private long p99Ms;
        private long maxMs;
        private double qps;

        private ProbeStats(
                String dependency,
                String serviceKey,
                String clusterKey,
                String resourceKey,
                String endpoint,
                String targetService,
                String targetResource,
                long warningP95Ms) {
            this.dependency = dependency;
            this.serviceKey = serviceKey;
            this.clusterKey = clusterKey;
            this.resourceKey = resourceKey;
            this.endpoint = endpoint;
            this.targetService = targetService;
            this.targetResource = targetResource;
            this.warningP95Ms = warningP95Ms;
        }

        private void record(ProbeOperation operation) {
            attemptedCount++;
            long started = System.nanoTime();
            try {
                operation.run();
                successCount++;
                latenciesNanos.add(System.nanoTime() - started);
            } catch (Exception error) {
                failureCount++;
                lastFailure = sanitizeFailure(error);
            }
        }

        private boolean shouldStopEarly(int requestedIterations) {
            return successCount == 0 && failureCount >= 3 && attemptedCount < requestedIterations;
        }

        private void finish(int requestedIterations) {
            if (attemptedCount < requestedIterations) {
                failureCount += requestedIterations - attemptedCount;
                attemptedCount = requestedIterations;
            }
            List<Long> millis = latenciesNanos.stream()
                    .map(Duration::ofNanos)
                    .map(Duration::toMillis)
                    .sorted()
                    .toList();
            if (!millis.isEmpty()) {
                minMs = millis.get(0);
                maxMs = millis.get(millis.size() - 1);
                avgMs = Math.round(millis.stream().mapToLong(Long::longValue).average().orElse(0.0));
                p95Ms = percentile(millis, 0.95);
                p99Ms = percentile(millis, 0.99);
            }
            long totalLatencyNanos = latenciesNanos.stream().mapToLong(Long::longValue).sum();
            qps = totalLatencyNanos <= 0 ? 0.0 : successCount / (totalLatencyNanos / 1_000_000_000.0);
        }

        private GovernanceSignalSeverity severity() {
            if (failureCount == attemptedCount && attemptedCount > 0) {
                return GovernanceSignalSeverity.CRITICAL;
            }
            if (failureCount > 0 || p95Ms >= warningP95Ms) {
                return GovernanceSignalSeverity.WARNING;
            }
            return GovernanceSignalSeverity.INFO;
        }

        private GovernanceSignalType signalType() {
            if (failureCount > 0) {
                if ("redis".equals(dependency)) {
                    return GovernanceSignalType.REDIS_TIMEOUT;
                }
                return GovernanceSignalType.DEPENDENCY_TIMEOUT;
            }
            if (p95Ms >= warningP95Ms) {
                return GovernanceSignalType.LATENCY;
            }
            return GovernanceSignalType.RESOURCE_EVENT;
        }

        private String outcome() {
            if (failureCount > 0) {
                return "FAILED";
            }
            if (p95Ms >= warningP95Ms) {
                return "SLOW";
            }
            return "OK";
        }

        private String durationBucket() {
            if (maxMs >= 2_000) {
                return "GT_2S";
            }
            if (maxMs >= 1_000) {
                return "GT_1S";
            }
            if (maxMs >= 100) {
                return "LT_1S";
            }
            return "LT_100_MS";
        }

        private Map<String, String> toSignalAttributes() {
            long total = attemptedCount;
            double failureRate = total == 0 ? 0.0 : failureCount / (double) total;
            return Map.ofEntries(
                    entry("source", "live-probe"),
                    entry("flowKey", "flow-live-" + dependency + "-probe"),
                    entry("endpoint", endpoint),
                    entry("targetService", targetService),
                    entry("targetResource", targetResource),
                    entry("component", dependency),
                    entry("operation", endpoint),
                    entry("durationMs", Long.toString(Math.max(maxMs, p95Ms))),
                    entry("spanDurationMs", Long.toString(p95Ms)),
                    entry("summary", summary()),
                    entry("metricTotal", Long.toString(total)),
                    entry("metricFailure", Integer.toString(failureCount)),
                    entry("metricFailureRate", decimal(failureRate)),
                    entry("metricTps", decimal(qps)),
                    entry("metricQps", decimal(qps)),
                    entry("metricMinMs", Long.toString(minMs)),
                    entry("metricMaxMs", Long.toString(maxMs)),
                    entry("metricAvgMs", Long.toString(avgMs)),
                    entry("metricP95Ms", Long.toString(p95Ms)),
                    entry("metricP99Ms", Long.toString(p99Ms)),
                    entry("probeStatus", outcome()),
                    entry("lastFailure", lastFailure));
        }

        private Map<String, Object> toResponse() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("dependency", dependency);
            item.put("serviceKey", serviceKey);
            item.put("targetService", targetService);
            item.put("attempted", attemptedCount);
            item.put("success", successCount);
            item.put("failure", failureCount);
            item.put("outcome", outcome());
            item.put("severity", severity().name());
            item.put("p95Ms", p95Ms);
            item.put("p99Ms", p99Ms);
            item.put("maxMs", maxMs);
            item.put("qps", qps);
            item.put("lastFailure", lastFailure);
            return item;
        }

        private String summary() {
            if (failureCount > 0) {
                return dependency + " live probe failed with " + lastFailure;
            }
            if (p95Ms >= warningP95Ms) {
                return dependency + " live probe p95 crossed warning threshold";
            }
            return dependency + " live probe healthy";
        }

        private static long percentile(List<Long> sortedValues, double percentile) {
            if (sortedValues.isEmpty()) {
                return 0;
            }
            int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
            return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
        }

        private static String sanitizeFailure(Exception error) {
            String simpleName = error.getClass().getSimpleName();
            if (simpleName == null || simpleName.isBlank()) {
                return "RuntimeFailure";
            }
            return simpleName.length() > 80 ? simpleName.substring(0, 80) : simpleName;
        }

        private static String decimal(double value) {
            return String.format(Locale.ROOT, "%.4f", value);
        }
    }
}
