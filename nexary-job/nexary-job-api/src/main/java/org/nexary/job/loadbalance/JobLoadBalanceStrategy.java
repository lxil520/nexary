package org.nexary.job.loadbalance;

import java.util.Locale;

/** Built-in load-balance strategies for local distributed job scheduling. */
public enum JobLoadBalanceStrategy {
    /** Assigns shards to workers by position. */
    ROUND_ROBIN("round_robin"),

    /** Picks a worker randomly for each shard. */
    RANDOM("random"),

    /** Assigns shards by stable hash so worker changes move fewer shards. */
    CONSISTENT_HASH("consistent_hash"),

    /** Prefers the worker with the fewest active executions. */
    LEAST_ACTIVE("least_active"),

    /** Always selects the first available worker. */
    FIRST_AVAILABLE("first_available");

    private final String id;

    JobLoadBalanceStrategy(String id) {
        this.id = id;
    }

    /** Returns the external configuration id. */
    public String id() {
        return id;
    }

    /** Parses a strategy id, accepting enum names and external ids. */
    public static JobLoadBalanceStrategy parse(String value) {
        if (value == null || value.isBlank()) {
            return ROUND_ROBIN;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (JobLoadBalanceStrategy strategy : values()) {
            if (strategy.id.equals(normalized) || strategy.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("unknown job load-balance strategy: " + value);
    }
}
