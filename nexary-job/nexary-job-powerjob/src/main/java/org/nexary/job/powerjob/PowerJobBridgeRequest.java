package org.nexary.job.powerjob;

import java.util.LinkedHashMap;
import java.util.Map;
import org.nexary.job.internal.JobCompatibilityCollections;

/** Provider-side request that maps PowerJob trigger metadata into Nexary execution metadata. */
public final class PowerJobBridgeRequest {
    private final String jobName;
    private final int shardIndex;
    private final int shardTotal;
    private final String instanceId;
    private final String taskId;
    private final String taskName;

    /** Creates a PowerJob bridge request with only a Nexary job name. */
    public PowerJobBridgeRequest(String jobName) {
        this(jobName, 0, 1, "", "", "");
    }

    /** Creates a PowerJob bridge request with shard and platform metadata. */
    public PowerJobBridgeRequest(
            String jobName,
            int shardIndex,
            int shardTotal,
            String instanceId,
            String taskId,
            String taskName) {
        this.jobName = jobName;
        this.shardIndex = Math.max(0, shardIndex);
        this.shardTotal = Math.max(1, shardTotal);
        this.instanceId = safe(instanceId);
        this.taskId = safe(taskId);
        this.taskName = safe(taskName);
    }

    public String jobName() {
        return jobName;
    }

    public int shardIndex() {
        return shardIndex;
    }

    public int shardTotal() {
        return shardTotal;
    }

    public String instanceId() {
        return instanceId;
    }

    public String taskId() {
        return taskId;
    }

    public String taskName() {
        return taskName;
    }

    /** Returns bounded provider metadata that can be stored on the execution record. */
    public Map<String, String> providerMetadata() {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("provider", "powerjob");
        putIfPresent(metadata, "instance_id", instanceId);
        putIfPresent(metadata, "task_id", taskId);
        putIfPresent(metadata, "task_name", taskName);
        metadata.put("shard_index", String.valueOf(shardIndex));
        metadata.put("shard_total", String.valueOf(shardTotal));
        return JobCompatibilityCollections.copyMap(metadata);
    }

    private static void putIfPresent(Map<String, String> metadata, String key, String value) {
        if (!value.isEmpty()) {
            metadata.put(key, value);
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }
}
