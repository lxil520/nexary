package org.nexary.governance.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only topology projection for the platform operations console. */
public final class GovernanceTopology {
    private final List<GovernanceServiceNode> services;
    private final List<GovernanceDependencyEdge> dependencies;
    private final List<GovernanceConnectorStatus> connectors;

    /** Creates a topology projection. */
    public GovernanceTopology(
            List<GovernanceServiceNode> services,
            List<GovernanceDependencyEdge> dependencies,
            List<GovernanceConnectorStatus> connectors) {
        this.services = immutableList(services);
        this.dependencies = immutableList(dependencies);
        this.connectors = immutableList(connectors);
    }

    /** Returns service nodes. */
    public List<GovernanceServiceNode> services() { return services; }
    /** Returns dependency edges. */
    public List<GovernanceDependencyEdge> dependencies() { return dependencies; }
    /** Returns connector statuses. */
    public List<GovernanceConnectorStatus> connectors() { return connectors; }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
