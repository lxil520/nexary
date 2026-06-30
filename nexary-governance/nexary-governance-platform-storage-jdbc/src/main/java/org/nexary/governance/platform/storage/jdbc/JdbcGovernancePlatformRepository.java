package org.nexary.governance.platform.storage.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nexary.governance.platform.GovernanceAsset;
import org.nexary.governance.platform.GovernanceAssetKind;
import org.nexary.governance.platform.GovernanceConnector;
import org.nexary.governance.platform.GovernanceConnectorKind;
import org.nexary.governance.platform.GovernanceConnectorState;
import org.nexary.governance.platform.GovernanceDependency;
import org.nexary.governance.platform.GovernanceDependencyKind;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSignalSeverity;
import org.nexary.governance.platform.GovernanceSignalType;
import org.nexary.governance.platform.server.GovernancePlatformRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** JDBC-backed repository for platform assets and low-cardinality signals. */
public class JdbcGovernancePlatformRepository implements GovernancePlatformRepository {
    private static final TypeReference<Map<String, String>> ATTRIBUTES_TYPE = new TypeReference<>() { };
    private final JdbcOperations jdbcOperations;
    private final ObjectMapper objectMapper;
    private final GovernancePlatformJdbcDialect dialect;

    /** Creates the repository and resolves the dialect from JDBC metadata. */
    public JdbcGovernancePlatformRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper) {
        this(jdbcOperations, objectMapper, resolveDialect(jdbcOperations));
    }

    /** Creates the repository with an explicit SQL dialect and initializes required tables. */
    public JdbcGovernancePlatformRepository(
            JdbcOperations jdbcOperations,
            ObjectMapper objectMapper,
            GovernancePlatformJdbcDialect dialect) {
        this.jdbcOperations = Objects.requireNonNull(jdbcOperations, "jdbcOperations");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        JdbcGovernancePlatformSchema.initialize(jdbcOperations, this.dialect);
    }

    /** Returns the SQL dialect used by this repository. */
    public GovernancePlatformJdbcDialect dialect() {
        return dialect;
    }

    @Override
    public void saveResourceReport(GovernancePlatformResourceReport report) {
        for (GovernanceAsset asset : report.assets()) {
            jdbcOperations.update(dialect.upsertAssetSql(), asset.kind().name(), asset.key(), asset.name(),
                    writeAttributes(asset.attributes()));
        }
        for (GovernanceDependency dependency : report.dependencies()) {
            jdbcOperations.update(dialect.upsertDependencySql(), dependency.sourceKey(), dependency.targetKey(),
                    dependency.kind().name(), dependency.resourceKey(), writeAttributes(dependency.attributes()));
        }
        for (GovernanceConnector connector : report.connectors()) {
            jdbcOperations.update(dialect.upsertConnectorSql(), connector.connectorKey(), connector.kind().name(),
                    connector.state().name(), connector.displayName(), connector.lastMessage(),
                    writeAttributes(connector.attributes()));
        }
    }

    @Override
    public void saveSignal(GovernanceSignal signal) {
        jdbcOperations.update("""
                insert into nexary_platform_signals(
                    workspace_key, environment_key, service_key, cluster_key, zone_key, resource_key,
                    signal_type, severity, outcome, duration_bucket, occurred_at, attributes)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, signal.workspaceKey(), signal.environmentKey(), signal.serviceKey(), signal.clusterKey(),
                signal.zoneKey(), signal.resourceKey(), signal.signalType().name(), signal.severity().name(),
                signal.outcome(), signal.durationBucket(), Timestamp.from(signal.timestamp()), writeAttributes(signal.attributes()));
    }

    @Override
    public List<GovernancePlatformResourceReport> resourceReports() {
        List<GovernanceAsset> assets = jdbcOperations.query("""
                select kind, asset_key, name, attributes from nexary_platform_assets
                """, (rs, rowNum) -> new GovernanceAsset(
                GovernanceAssetKind.valueOf(rs.getString("kind")),
                rs.getString("asset_key"),
                rs.getString("name"),
                readAttributes(rs.getString("attributes"))));
        List<GovernanceDependency> dependencies = jdbcOperations.query("""
                select source_key, target_key, kind, resource_key, attributes from nexary_platform_dependencies
                """, (rs, rowNum) -> new GovernanceDependency(
                rs.getString("source_key"),
                rs.getString("target_key"),
                GovernanceDependencyKind.valueOf(rs.getString("kind")),
                rs.getString("resource_key"),
                readAttributes(rs.getString("attributes"))));
        List<GovernanceConnector> connectors = jdbcOperations.query("""
                select connector_key, kind, state, display_name, last_message, attributes from nexary_platform_connectors
                """, (rs, rowNum) -> new GovernanceConnector(
                rs.getString("connector_key"),
                GovernanceConnectorKind.valueOf(rs.getString("kind")),
                GovernanceConnectorState.valueOf(rs.getString("state")),
                rs.getString("display_name"),
                rs.getString("last_message"),
                readAttributes(rs.getString("attributes"))));
        return List.of(new GovernancePlatformResourceReport(assets, dependencies, connectors));
    }

    @Override
    public List<GovernanceSignal> signals() {
        return jdbcOperations.query("""
                select workspace_key, environment_key, service_key, cluster_key, zone_key, resource_key,
                       signal_type, severity, outcome, duration_bucket, occurred_at, attributes
                  from nexary_platform_signals
                 order by occurred_at desc
                 limit 1000
                """, (rs, rowNum) -> new GovernanceSignal(
                rs.getString("workspace_key"),
                rs.getString("environment_key"),
                rs.getString("service_key"),
                rs.getString("cluster_key"),
                rs.getString("zone_key"),
                rs.getString("resource_key"),
                GovernanceSignalType.valueOf(rs.getString("signal_type")),
                GovernanceSignalSeverity.valueOf(rs.getString("severity")),
                rs.getString("outcome"),
                rs.getString("duration_bucket"),
                rs.getTimestamp("occurred_at").toInstant(),
                readAttributes(rs.getString("attributes"))));
    }

    private String writeAttributes(Map<String, String> attributes) {
        try {
            return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid platform attributes", e);
        }
    }

    private Map<String, String> readAttributes(String attributes) {
        try {
            return objectMapper.readValue(attributes == null ? "{}" : attributes, ATTRIBUTES_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid platform attributes", e);
        }
    }

    private static GovernancePlatformJdbcDialect resolveDialect(JdbcOperations jdbcOperations) {
        Objects.requireNonNull(jdbcOperations, "jdbcOperations");
        String databaseName = jdbcOperations.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
        return GovernancePlatformJdbcDialects.resolve(databaseName);
    }
}
