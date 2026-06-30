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
    }
}
