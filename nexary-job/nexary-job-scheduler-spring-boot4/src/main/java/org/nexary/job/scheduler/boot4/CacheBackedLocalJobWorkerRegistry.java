package org.nexary.job.scheduler.boot4;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nexary.cache.CacheClient;
import org.nexary.cache.CacheKey;
import org.nexary.cache.LockHandle;
import org.nexary.job.JobSchedule;
import org.nexary.job.internal.JobCompatibilityCollections;

/** Cache-backed worker registry used by the local distributed scheduler. */
class CacheBackedLocalJobWorkerRegistry implements LocalJobWorkerRegistry {
    private static final String NAMESPACE = "job-scheduler";

    private final Optional<CacheClient> cacheClient;
    private final LocalJobSchedulerProperties properties;

    CacheBackedLocalJobWorkerRegistry(Optional<CacheClient> cacheClient, LocalJobSchedulerProperties properties) {
        this.cacheClient = cacheClient;
        this.properties = properties;
    }

    @Override
    public String currentWorkerId(JobSchedule schedule) {
        if (schedule.workerId() != null) {
            return schedule.workerId();
        }
        String workerId = properties.getWorkerId();
        return workerId == null || workerId.trim().isEmpty() ? null : workerId.trim();
    }

    @Override
    public List<String> workerIds(JobSchedule schedule) {
        if (!schedule.workerIds().isEmpty()) {
            return schedule.workerIds();
        }
        List<String> configuredWorkers = configuredWorkers();
        if (!cacheClient.isPresent() || !properties.isHeartbeatEnabled() || currentWorkerId(schedule) == null) {
            return configuredWorkers;
        }
        heartbeat();
        List<String> activeWorkers = activeWorkers();
        return activeWorkers.isEmpty() ? configuredWorkers : activeWorkers;
    }

    @Override
    public void heartbeat() {
        if (!supportsHeartbeat()) {
            return;
        }
        String workerId = configuredCurrentWorkerId();
        CacheClient cache = cacheClient.get();
        Optional<LockHandle> lock = cache.tryLock(lockKey(), Duration.ZERO, properties.getHeartbeatTtl());
        if (!lock.isPresent()) {
            return;
        }
        try (LockHandle ignored = lock.get()) {
            Map<String, Long> topology = topology();
            long now = Instant.now().toEpochMilli();
            long expiresBefore = now - properties.getHeartbeatTtl().toMillis();
            topology.entrySet().removeIf(entry -> entry.getValue() <= expiresBefore);
            topology.put(workerId, now);
            cache.put(topologyKey(), topology, properties.getHeartbeatTtl().plus(properties.getHeartbeatInterval()));
        }
    }

    @Override
    public boolean supportsHeartbeat() {
        return cacheClient.isPresent() && properties.isHeartbeatEnabled() && configuredCurrentWorkerId() != null;
    }

    private List<String> activeWorkers() {
        Map<String, Long> topology = topology();
        long expiresBefore = Instant.now().toEpochMilli() - properties.getHeartbeatTtl().toMillis();
        return JobCompatibilityCollections.collectList(topology.entrySet().stream()
                .filter(entry -> entry.getValue() > expiresBefore)
                .map(Map.Entry::getKey)
                .sorted());
    }

    private Map<String, Long> topology() {
        if (!cacheClient.isPresent()) {
            return new LinkedHashMap<>();
        }
        Optional<Object> stored = cacheClient.get().get(topologyKey(), Object.class);
        Map<String, Long> topology = new LinkedHashMap<>();
        stored.ifPresent(value -> {
            if (value instanceof Map<?, ?>) {
                Map<?, ?> values = (Map<?, ?>) value;
                values.forEach((key, timestamp) -> {
                    if (key != null && timestamp instanceof Number) {
                        topology.put(String.valueOf(key), ((Number) timestamp).longValue());
                    }
                });
            }
        });
        return topology;
    }

    private List<String> configuredWorkers() {
        return JobCompatibilityCollections.collectList(properties.getWorkers().stream()
                .filter(worker -> worker != null && !worker.trim().isEmpty())
                .map(String::trim)
                .sorted());
    }

    private String configuredCurrentWorkerId() {
        String workerId = properties.getWorkerId();
        return workerId == null || workerId.trim().isEmpty() ? null : workerId.trim();
    }

    private CacheKey topologyKey() {
        return CacheKey.of(NAMESPACE, "topology:" + properties.getTopology());
    }

    private CacheKey lockKey() {
        return CacheKey.of(NAMESPACE, "topology-lock:" + properties.getTopology());
    }
}
