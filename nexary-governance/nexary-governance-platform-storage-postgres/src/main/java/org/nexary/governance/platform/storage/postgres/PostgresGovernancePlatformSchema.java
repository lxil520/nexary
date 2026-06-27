package org.nexary.governance.platform.storage.postgres;

import org.springframework.jdbc.core.JdbcOperations;

import java.util.Objects;

/** Schema helper for the Postgres governance platform storage module. */
public final class PostgresGovernancePlatformSchema {
    private PostgresGovernancePlatformSchema() {
    }

    /** Creates platform storage tables when they do not exist. */
    public static void initialize(JdbcOperations jdbcOperations) {
        Objects.requireNonNull(jdbcOperations, "jdbcOperations");
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
                    id bigserial primary key,
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
                """);
    }
}
