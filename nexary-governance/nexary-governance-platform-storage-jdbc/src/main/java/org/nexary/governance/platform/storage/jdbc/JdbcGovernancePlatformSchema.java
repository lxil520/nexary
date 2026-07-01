package org.nexary.governance.platform.storage.jdbc;

import org.springframework.jdbc.core.JdbcOperations;

import java.util.Objects;

/** Schema helper for governance platform JDBC storage. */
public final class JdbcGovernancePlatformSchema {
    private JdbcGovernancePlatformSchema() {
    }

    /** Creates platform storage tables when they do not exist. */
    public static void initialize(JdbcOperations jdbcOperations, GovernancePlatformJdbcDialect dialect) {
        Objects.requireNonNull(jdbcOperations, "jdbcOperations");
        GovernancePlatformJdbcDialect safeDialect = Objects.requireNonNull(dialect, "dialect");
        jdbcOperations.execute("""
                create table if not exists nexary_platform_assets (
                    kind varchar(64) not null,
                    asset_key varchar(160) not null primary key,
                    name varchar(160) not null,
                    attributes text not null
                )
                """);
        jdbcOperations.execute("""
                create table if not exists nexary_platform_dependencies (
                    source_key varchar(160) not null,
                    target_key varchar(160) not null,
                    kind varchar(64) not null,
                    resource_key varchar(160) not null,
                    attributes text not null,
                    primary key (source_key, target_key, resource_key)
                )
                """);
        jdbcOperations.execute("""
                create table if not exists nexary_platform_connectors (
                    connector_key varchar(160) not null primary key,
                    kind varchar(64) not null,
                    state varchar(64) not null,
                    display_name varchar(160) not null,
                    last_message varchar(160) not null,
                    attributes text not null
                )
                """);
        jdbcOperations.execute("""
                create table if not exists nexary_platform_signals (
                    id %s,
                    workspace_key varchar(160) not null,
                    environment_key varchar(160) not null,
                    service_key varchar(160) not null,
                    cluster_key varchar(160) not null,
                    zone_key varchar(160) not null,
                    resource_key varchar(160) not null,
                    signal_type varchar(64) not null,
                    severity varchar(64) not null,
                    outcome varchar(64) not null,
                    duration_bucket varchar(64) not null,
                    occurred_at timestamp not null,
                    attributes text not null
                )
                """.formatted(safeDialect.signalIdColumn()));
        jdbcOperations.execute("""
                create table if not exists governance_review_plan (
                    plan_key varchar(160) not null primary key,
                    incident_key varchar(160) not null,
                    title varchar(160) not null,
                    state varchar(64) not null,
                    risk varchar(64) not null,
                    target_kind varchar(64) not null,
                    target_key varchar(160) not null,
                    target_display_name varchar(160) not null,
                    diffs text not null,
                    service_key varchar(160) not null,
                    resource_key varchar(160) not null,
                    proposed_action varchar(160) not null,
                    evidence_count integer not null,
                    impacted_service_count integer not null,
                    impacted_instance_count integer not null,
                    created_at timestamp not null,
                    updated_at timestamp not null
                )
                """);
        jdbcOperations.execute("""
                create table if not exists governance_plan_evidence (
                    plan_key varchar(160) not null,
                    position integer not null,
                    signal_type varchar(64) not null,
                    severity varchar(64) not null,
                    service_key varchar(160) not null,
                    cluster_key varchar(160) not null,
                    zone_key varchar(160) not null,
                    resource_key varchar(160) not null,
                    outcome varchar(64) not null,
                    duration_bucket varchar(64) not null,
                    message varchar(160) not null,
                    reference_type varchar(64) not null,
                    reference_key varchar(160) not null,
                    occurred_at timestamp,
                    primary key (plan_key, position)
                )
                """);
        jdbcOperations.execute("""
                create table if not exists governance_notification_route (
                    route_key varchar(160) not null primary key,
                    channel varchar(64) not null,
                    display_name varchar(160) not null,
                    target_team varchar(160) not null,
                    min_severity varchar(64) not null,
                    mode varchar(64) not null,
                    state varchar(64) not null,
                    test_enabled boolean not null,
                    last_message varchar(160) not null,
                    attributes text not null
                )
                """);
        jdbcOperations.execute("""
                create table if not exists governance_notification_test (
                    test_key varchar(160) not null primary key,
                    route_key varchar(160) not null,
                    accepted boolean not null,
                    status varchar(64) not null,
                    message varchar(160) not null,
                    attempted_at timestamp not null,
                    preview_incident_key varchar(160) not null,
                    preview_subject varchar(160) not null,
                    preview_body varchar(160) not null
                )
                """);
        jdbcOperations.execute("""
                create table if not exists governance_audit_record (
                    audit_key varchar(160) not null primary key,
                    action varchar(64) not null,
                    subject_key varchar(160) not null,
                    result varchar(64) not null,
                    message varchar(160) not null,
                    created_at timestamp not null
                )
                """);
    }
}
