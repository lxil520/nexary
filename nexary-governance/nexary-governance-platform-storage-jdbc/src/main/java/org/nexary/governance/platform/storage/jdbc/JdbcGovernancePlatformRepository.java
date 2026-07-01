package org.nexary.governance.platform.storage.jdbc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.nexary.governance.platform.EvidenceItem;
import org.nexary.governance.platform.GovernanceAuditAction;
import org.nexary.governance.platform.GovernanceAuditRecord;
import org.nexary.governance.platform.GovernanceAsset;
import org.nexary.governance.platform.GovernanceAssetKind;
import org.nexary.governance.platform.GovernanceConnectorAccessMode;
import org.nexary.governance.platform.GovernanceConnectorAuthMode;
import org.nexary.governance.platform.GovernanceConnectorCapability;
import org.nexary.governance.platform.GovernanceConnectorConfig;
import org.nexary.governance.platform.GovernanceConnector;
import org.nexary.governance.platform.GovernanceConnectorKind;
import org.nexary.governance.platform.GovernanceConnectorState;
import org.nexary.governance.platform.GovernanceConnectorTestResult;
import org.nexary.governance.platform.GovernanceDependency;
import org.nexary.governance.platform.GovernanceDependencyKind;
import org.nexary.governance.platform.GovernanceNotificationMode;
import org.nexary.governance.platform.GovernanceNotificationPreview;
import org.nexary.governance.platform.GovernanceNotificationRoute;
import org.nexary.governance.platform.GovernanceNotificationTestResult;
import org.nexary.governance.platform.GovernancePlatformResourceReport;
import org.nexary.governance.platform.GovernancePlanDiff;
import org.nexary.governance.platform.GovernancePlanRisk;
import org.nexary.governance.platform.GovernancePlanState;
import org.nexary.governance.platform.GovernancePlanTarget;
import org.nexary.governance.platform.GovernancePlanTargetKind;
import org.nexary.governance.platform.GovernanceReviewPlan;
import org.nexary.governance.platform.GovernanceServiceMapping;
import org.nexary.governance.platform.GovernanceSignal;
import org.nexary.governance.platform.GovernanceSignalSeverity;
import org.nexary.governance.platform.GovernanceSignalType;
import org.nexary.governance.platform.server.GovernancePlatformRepository;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcOperations;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** JDBC-backed repository for platform assets and low-cardinality signals. */
public class JdbcGovernancePlatformRepository implements GovernancePlatformRepository {
    private static final TypeReference<Map<String, String>> ATTRIBUTES_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, String>>> DIFFS_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };
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
    public void saveReviewPlan(GovernanceReviewPlan plan) {
        Objects.requireNonNull(plan, "plan");
        jdbcOperations.update("delete from governance_plan_evidence where plan_key = ?", plan.planKey());
        jdbcOperations.update("delete from governance_review_plan where plan_key = ?", plan.planKey());
        jdbcOperations.update("""
                insert into governance_review_plan(
                    plan_key, incident_key, title, state, risk, target_kind, target_key, target_display_name,
                    diffs, service_key, resource_key, proposed_action, evidence_count,
                    impacted_service_count, impacted_instance_count, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                plan.planKey(),
                plan.incidentKey(),
                plan.title(),
                plan.state().name(),
                plan.risk().name(),
                plan.target().kind().name(),
                plan.target().targetKey(),
                plan.target().displayName(),
                writeDiffs(plan.diffs()),
                plan.serviceKey(),
                plan.resourceKey(),
                plan.proposedAction(),
                plan.evidenceCount(),
                plan.impactedServiceCount(),
                plan.impactedInstanceCount(),
                Timestamp.from(plan.createdAt()),
                Timestamp.from(plan.updatedAt()));
        int index = 0;
        for (EvidenceItem evidence : plan.evidence()) {
            index++;
            jdbcOperations.update("""
                    insert into governance_plan_evidence(
                        plan_key, position, signal_type, severity, service_key, cluster_key, zone_key, resource_key,
                        outcome, duration_bucket, message, reference_type, reference_key, occurred_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    plan.planKey(),
                    index,
                    evidence.signalType().name(),
                    evidence.severity().name(),
                    evidence.serviceKey(),
                    evidence.clusterKey(),
                    evidence.zoneKey(),
                    evidence.resourceKey(),
                    evidence.outcome(),
                    evidence.durationBucket(),
                    evidence.message(),
                    evidence.referenceType(),
                    evidence.referenceKey(),
                    evidence.timestamp() == null ? null : Timestamp.from(evidence.timestamp()));
        }
    }

    @Override
    public void saveNotificationRoute(GovernanceNotificationRoute route) {
        Objects.requireNonNull(route, "route");
        jdbcOperations.update("delete from governance_notification_route where route_key = ?", route.routeKey());
        jdbcOperations.update("""
                insert into governance_notification_route(
                    route_key, channel, display_name, target_team, min_severity, mode, state,
                    test_enabled, last_message, attributes)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                route.routeKey(),
                route.channel(),
                route.displayName(),
                route.targetTeam(),
                route.minSeverity().name(),
                route.mode().name(),
                route.state().name(),
                route.testEnabled(),
                route.lastMessage(),
                writeAttributes(route.attributes()));
    }

    @Override
    public void saveConnectorConfig(GovernanceConnectorConfig config) {
        Objects.requireNonNull(config, "config");
        jdbcOperations.update("delete from governance_connector_config where connector_key = ?", config.connectorKey());
        jdbcOperations.update("""
                insert into governance_connector_config(
                    connector_key, kind, display_name, endpoint, auth_mode, access_mode, state,
                    test_enabled, capabilities, last_message, attributes, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                config.connectorKey(),
                config.kind().name(),
                config.displayName(),
                config.endpoint(),
                config.authMode().name(),
                config.accessMode().name(),
                config.state().name(),
                config.testEnabled(),
                writeCapabilities(config.capabilities()),
                config.lastMessage(),
                writeAttributes(config.attributes()),
                Timestamp.from(config.createdAt()),
                Timestamp.from(config.updatedAt()));
    }

    @Override
    public void saveConnectorTestResult(GovernanceConnectorTestResult result) {
        Objects.requireNonNull(result, "result");
        jdbcOperations.update("delete from governance_connector_test where test_key = ?", result.testKey());
        jdbcOperations.update("""
                insert into governance_connector_test(
                    test_key, connector_key, accepted, status, message, tested_at, capabilities)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                result.testKey(),
                result.connectorKey(),
                result.accepted(),
                result.status(),
                result.message(),
                Timestamp.from(result.testedAt()),
                writeCapabilities(result.capabilities()));
    }

    @Override
    public void saveServiceMapping(GovernanceServiceMapping mapping) {
        Objects.requireNonNull(mapping, "mapping");
        jdbcOperations.update("delete from governance_service_mapping where mapping_key = ?", mapping.mappingKey());
        jdbcOperations.update("""
                insert into governance_service_mapping(
                    mapping_key, service_key, connector_key, source_kind, external_key,
                    resource_kind, confidence, attributes, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                mapping.mappingKey(),
                mapping.serviceKey(),
                mapping.connectorKey(),
                mapping.sourceKind().name(),
                mapping.externalKey(),
                mapping.resourceKind(),
                mapping.confidence(),
                writeAttributes(mapping.attributes()),
                Timestamp.from(mapping.updatedAt()));
    }

    @Override
    public void saveNotificationTestResult(GovernanceNotificationTestResult result) {
        Objects.requireNonNull(result, "result");
        GovernanceNotificationPreview preview = result.preview();
        jdbcOperations.update("delete from governance_notification_test where test_key = ?", result.testKey());
        jdbcOperations.update("""
                insert into governance_notification_test(
                    test_key, route_key, accepted, status, message, attempted_at,
                    preview_incident_key, preview_subject, preview_body)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                result.testKey(),
                result.routeKey(),
                result.accepted(),
                result.status(),
                result.message(),
                Timestamp.from(result.attemptedAt()),
                preview == null ? "none" : preview.incidentKey(),
                preview == null ? "TEST / DRY-RUN notification" : preview.subject(),
                preview == null ? "No preview rendered" : preview.body());
    }

    @Override
    public void saveAuditRecord(GovernanceAuditRecord record) {
        Objects.requireNonNull(record, "record");
        jdbcOperations.update("delete from governance_audit_record where audit_key = ?", record.auditKey());
        jdbcOperations.update("""
                insert into governance_audit_record(audit_key, action, subject_key, result, message, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                record.auditKey(),
                record.action().name(),
                record.subjectKey(),
                record.result(),
                record.message(),
                Timestamp.from(record.createdAt()));
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

    @Override
    public List<GovernanceReviewPlan> reviewPlans() {
        return jdbcOperations.query("""
                select plan_key, incident_key, title, state, risk, target_kind, target_key, target_display_name,
                       diffs, service_key, resource_key, proposed_action, evidence_count,
                       impacted_service_count, impacted_instance_count, created_at, updated_at
                  from governance_review_plan
                 order by updated_at desc
                 limit 200
                """, (rs, rowNum) -> new GovernanceReviewPlan(
                rs.getString("plan_key"),
                rs.getString("incident_key"),
                rs.getString("title"),
                GovernancePlanState.valueOf(rs.getString("state")),
                GovernancePlanRisk.valueOf(rs.getString("risk")),
                new GovernancePlanTarget(
                        GovernancePlanTargetKind.valueOf(rs.getString("target_kind")),
                        rs.getString("target_key"),
                        rs.getString("target_display_name")),
                readDiffs(rs.getString("diffs")),
                rs.getString("service_key"),
                rs.getString("resource_key"),
                rs.getString("proposed_action"),
                evidenceForPlan(rs.getString("plan_key")),
                rs.getInt("evidence_count"),
                rs.getInt("impacted_service_count"),
                rs.getInt("impacted_instance_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
    }

    @Override
    public Optional<GovernanceReviewPlan> reviewPlan(String planKey) {
        return reviewPlans().stream()
                .filter(plan -> plan.planKey().equals(planKey))
                .findFirst();
    }

    @Override
    public List<GovernanceNotificationRoute> notificationRoutes() {
        return jdbcOperations.query("""
                select route_key, channel, display_name, target_team, min_severity, mode, state,
                       test_enabled, last_message, attributes
                  from governance_notification_route
                 order by route_key
                 limit 200
                """, (rs, rowNum) -> new GovernanceNotificationRoute(
                rs.getString("route_key"),
                rs.getString("channel"),
                rs.getString("display_name"),
                rs.getString("target_team"),
                GovernanceSignalSeverity.valueOf(rs.getString("min_severity")),
                GovernanceNotificationMode.valueOf(rs.getString("mode")),
                GovernanceConnectorState.valueOf(rs.getString("state")),
                rs.getBoolean("test_enabled"),
                rs.getString("last_message"),
                readAttributes(rs.getString("attributes"))));
    }

    @Override
    public Optional<GovernanceNotificationRoute> notificationRoute(String routeKey) {
        return notificationRoutes().stream()
                .filter(route -> route.routeKey().equals(routeKey))
                .findFirst();
    }

    @Override
    public List<GovernanceConnectorConfig> connectorConfigs() {
        return jdbcOperations.query("""
                select connector_key, kind, display_name, endpoint, auth_mode, access_mode, state,
                       test_enabled, capabilities, last_message, attributes, created_at, updated_at
                  from governance_connector_config
                 order by connector_key
                 limit 200
                """, (rs, rowNum) -> new GovernanceConnectorConfig(
                rs.getString("connector_key"),
                GovernanceConnectorKind.valueOf(rs.getString("kind")),
                rs.getString("display_name"),
                rs.getString("endpoint"),
                GovernanceConnectorAuthMode.valueOf(rs.getString("auth_mode")),
                GovernanceConnectorAccessMode.valueOf(rs.getString("access_mode")),
                GovernanceConnectorState.valueOf(rs.getString("state")),
                rs.getBoolean("test_enabled"),
                readCapabilities(rs.getString("capabilities")),
                rs.getString("last_message"),
                readAttributes(rs.getString("attributes")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
    }

    @Override
    public Optional<GovernanceConnectorConfig> connectorConfig(String connectorKey) {
        return connectorConfigs().stream()
                .filter(config -> config.connectorKey().equals(connectorKey))
                .findFirst();
    }

    @Override
    public List<GovernanceConnectorTestResult> connectorTestResults() {
        return jdbcOperations.query("""
                select test_key, connector_key, accepted, status, message, tested_at, capabilities
                  from governance_connector_test
                 order by tested_at desc
                 limit 200
                """, (rs, rowNum) -> new GovernanceConnectorTestResult(
                rs.getString("test_key"),
                rs.getString("connector_key"),
                rs.getBoolean("accepted"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getTimestamp("tested_at").toInstant(),
                readCapabilities(rs.getString("capabilities"))));
    }

    @Override
    public List<GovernanceServiceMapping> serviceMappings() {
        return jdbcOperations.query("""
                select mapping_key, service_key, connector_key, source_kind, external_key,
                       resource_kind, confidence, attributes, updated_at
                  from governance_service_mapping
                 order by service_key, source_kind
                 limit 500
                """, (rs, rowNum) -> new GovernanceServiceMapping(
                rs.getString("mapping_key"),
                rs.getString("service_key"),
                rs.getString("connector_key"),
                GovernanceConnectorKind.valueOf(rs.getString("source_kind")),
                rs.getString("external_key"),
                rs.getString("resource_kind"),
                rs.getDouble("confidence"),
                readAttributes(rs.getString("attributes")),
                rs.getTimestamp("updated_at").toInstant()));
    }

    @Override
    public Optional<GovernanceServiceMapping> serviceMapping(String mappingKey) {
        return serviceMappings().stream()
                .filter(mapping -> mapping.mappingKey().equals(mappingKey))
                .findFirst();
    }

    @Override
    public List<GovernanceNotificationTestResult> notificationTestResults() {
        return jdbcOperations.query("""
                select test_key, route_key, accepted, status, message, attempted_at,
                       preview_incident_key, preview_subject, preview_body
                  from governance_notification_test
                 order by attempted_at desc
                 limit 200
                """, (rs, rowNum) -> new GovernanceNotificationTestResult(
                rs.getString("test_key"),
                rs.getString("route_key"),
                rs.getBoolean("accepted"),
                rs.getString("status"),
                rs.getString("message"),
                rs.getTimestamp("attempted_at").toInstant(),
                new GovernanceNotificationPreview(
                        rs.getString("route_key"),
                        rs.getString("preview_incident_key"),
                        rs.getString("preview_subject"),
                        rs.getString("preview_body"),
                        List.of("platform-team"),
                        GovernanceNotificationMode.TEST,
                        rs.getTimestamp("attempted_at").toInstant())));
    }

    @Override
    public List<GovernanceAuditRecord> auditRecords() {
        return jdbcOperations.query("""
                select audit_key, action, subject_key, result, message, created_at
                  from governance_audit_record
                 order by created_at desc
                 limit 500
                """, (rs, rowNum) -> new GovernanceAuditRecord(
                rs.getString("audit_key"),
                GovernanceAuditAction.valueOf(rs.getString("action")),
                rs.getString("subject_key"),
                rs.getString("result"),
                rs.getString("message"),
                rs.getTimestamp("created_at").toInstant()));
    }

    private List<EvidenceItem> evidenceForPlan(String planKey) {
        return jdbcOperations.query("""
                select signal_type, severity, service_key, cluster_key, zone_key, resource_key,
                       outcome, duration_bucket, message, reference_type, reference_key, occurred_at
                  from governance_plan_evidence
                 where plan_key = ?
                 order by position
                """, (rs, rowNum) -> {
            Timestamp occurredAt = rs.getTimestamp("occurred_at");
            return new EvidenceItem(
                    GovernanceSignalType.valueOf(rs.getString("signal_type")),
                    GovernanceSignalSeverity.valueOf(rs.getString("severity")),
                    rs.getString("service_key"),
                    rs.getString("cluster_key"),
                    rs.getString("zone_key"),
                    rs.getString("resource_key"),
                    rs.getString("outcome"),
                    rs.getString("duration_bucket"),
                    rs.getString("message"),
                    rs.getString("reference_type"),
                    rs.getString("reference_key"),
                    occurredAt == null ? null : occurredAt.toInstant());
        }, planKey);
    }

    private String writeDiffs(List<GovernancePlanDiff> diffs) {
        List<Map<String, String>> rows = diffs == null ? List.of() : diffs.stream()
                .map(diff -> {
                    Map<String, String> row = new LinkedHashMap<>();
                    row.put("fieldKey", diff.fieldKey());
                    row.put("beforeValue", diff.beforeValue());
                    row.put("afterValue", diff.afterValue());
                    row.put("reason", diff.reason());
                    return row;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid platform diffs", e);
        }
    }

    private List<GovernancePlanDiff> readDiffs(String diffs) {
        try {
            List<Map<String, String>> rows = objectMapper.readValue(diffs == null ? "[]" : diffs, DIFFS_TYPE);
            List<GovernancePlanDiff> items = new ArrayList<>();
            for (Map<String, String> row : rows) {
                items.add(new GovernancePlanDiff(
                        row.getOrDefault("fieldKey", "unknown"),
                        row.getOrDefault("beforeValue", "unknown"),
                        row.getOrDefault("afterValue", "review-required"),
                        row.getOrDefault("reason", "Evidence requires review")));
            }
            return items;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid platform diffs", e);
        }
    }

    private String writeCapabilities(List<GovernanceConnectorCapability> capabilities) {
        try {
            List<String> values = capabilities == null ? List.of() : capabilities.stream()
                    .map(GovernanceConnectorCapability::name)
                    .toList();
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid platform connector capabilities", e);
        }
    }

    private List<GovernanceConnectorCapability> readCapabilities(String capabilities) {
        try {
            List<String> values = objectMapper.readValue(capabilities == null ? "[]" : capabilities, STRING_LIST_TYPE);
            return values.stream()
                    .map(GovernanceConnectorCapability::valueOf)
                    .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid platform connector capabilities", e);
        }
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
