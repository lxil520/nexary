package org.nexary.governance.platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Batch resource report sent by SDKs, samples, or connectors to the platform server. */
public final class GovernancePlatformResourceReport {
    private final List<GovernanceAsset> assets;
    private final List<GovernanceDependency> dependencies;
    private final List<GovernanceConnector> connectors;

    /** Creates a resource report. */
    public GovernancePlatformResourceReport(
            List<GovernanceAsset> assets,
            List<GovernanceDependency> dependencies,
            List<GovernanceConnector> connectors) {
        this.assets = immutableList(assets);
        this.dependencies = immutableList(dependencies);
        this.connectors = immutableList(connectors);
    }

    /** Returns assets included in this report. */
    public List<GovernanceAsset> assets() { return assets; }
    /** Returns dependencies included in this report. */
    public List<GovernanceDependency> dependencies() { return dependencies; }
    /** Returns connectors included in this report. */
    public List<GovernanceConnector> connectors() { return connectors; }

    private static <T> List<T> immutableList(List<T> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
