package org.nexary.governance.platform.storage.jdbc;

import java.util.Locale;

/** Built-in JDBC dialects for governance platform storage. */
public final class GovernancePlatformJdbcDialects {
    private static final GovernancePlatformJdbcDialect POSTGRES = new PostgresDialect();
    private static final GovernancePlatformJdbcDialect MYSQL = new MysqlDialect();
    private static final GovernancePlatformJdbcDialect SQLITE = new SqliteDialect();

    private GovernancePlatformJdbcDialects() {
    }

    /** Returns the PostgreSQL dialect. */
    public static GovernancePlatformJdbcDialect postgres() {
        return POSTGRES;
    }

    /** Returns the MySQL dialect. */
    public static GovernancePlatformJdbcDialect mysql() {
        return MYSQL;
    }

    /** Returns the SQLite dialect. */
    public static GovernancePlatformJdbcDialect sqlite() {
        return SQLITE;
    }

    /** Resolves a dialect from a JDBC database product name or explicit dialect name. */
    public static GovernancePlatformJdbcDialect resolve(String databaseName) {
        String normalized = databaseName == null ? "" : databaseName.toLowerCase(Locale.ROOT);
        if (normalized.contains("mysql") || normalized.contains("mariadb")) {
            return mysql();
        }
        if (normalized.contains("sqlite")) {
            return sqlite();
        }
        return postgres();
    }

    private abstract static class BaseDialect implements GovernancePlatformJdbcDialect {
        @Override
        public String upsertAssetSql() {
            return """
                    insert into nexary_platform_assets(kind, asset_key, name, attributes)
                    values (?, ?, ?, ?)
                    """;
        }

        @Override
        public String upsertDependencySql() {
            return """
                    insert into nexary_platform_dependencies(source_key, target_key, kind, resource_key, attributes)
                    values (?, ?, ?, ?, ?)
                    """;
        }

        @Override
        public String upsertConnectorSql() {
            return """
                    insert into nexary_platform_connectors(connector_key, kind, state, display_name, last_message, attributes)
                    values (?, ?, ?, ?, ?, ?)
                    """;
        }
    }

    private static final class PostgresDialect extends BaseDialect {
        @Override
        public String name() {
            return "postgres";
        }

        @Override
        public String signalIdColumn() {
            return "bigserial primary key";
        }

        @Override
        public String upsertAssetSql() {
            return super.upsertAssetSql() + """
                    on conflict (asset_key) do update set
                        kind = excluded.kind,
                        name = excluded.name,
                        attributes = excluded.attributes
                    """;
        }

        @Override
        public String upsertDependencySql() {
            return super.upsertDependencySql() + """
                    on conflict (source_key, target_key, resource_key) do update set
                        kind = excluded.kind,
                        attributes = excluded.attributes
                    """;
        }

        @Override
        public String upsertConnectorSql() {
            return super.upsertConnectorSql() + """
                    on conflict (connector_key) do update set
                        kind = excluded.kind,
                        state = excluded.state,
                        display_name = excluded.display_name,
                        last_message = excluded.last_message,
                        attributes = excluded.attributes
                    """;
        }
    }

    private static final class MysqlDialect extends BaseDialect {
        @Override
        public String name() {
            return "mysql";
        }

        @Override
        public String signalIdColumn() {
            return "bigint auto_increment primary key";
        }

        @Override
        public String upsertAssetSql() {
            return super.upsertAssetSql() + """
                    on duplicate key update
                        kind = values(kind),
                        name = values(name),
                        attributes = values(attributes)
                    """;
        }

        @Override
        public String upsertDependencySql() {
            return super.upsertDependencySql() + """
                    on duplicate key update
                        kind = values(kind),
                        attributes = values(attributes)
                    """;
        }

        @Override
        public String upsertConnectorSql() {
            return super.upsertConnectorSql() + """
                    on duplicate key update
                        kind = values(kind),
                        state = values(state),
                        display_name = values(display_name),
                        last_message = values(last_message),
                        attributes = values(attributes)
                    """;
        }
    }

    private static final class SqliteDialect extends BaseDialect {
        @Override
        public String name() {
            return "sqlite";
        }

        @Override
        public String signalIdColumn() {
            return "integer primary key autoincrement";
        }

        @Override
        public String upsertAssetSql() {
            return super.upsertAssetSql() + """
                    on conflict(asset_key) do update set
                        kind = excluded.kind,
                        name = excluded.name,
                        attributes = excluded.attributes
                    """;
        }

        @Override
        public String upsertDependencySql() {
            return super.upsertDependencySql() + """
                    on conflict(source_key, target_key, resource_key) do update set
                        kind = excluded.kind,
                        attributes = excluded.attributes
                    """;
        }

        @Override
        public String upsertConnectorSql() {
            return super.upsertConnectorSql() + """
                    on conflict(connector_key) do update set
                        kind = excluded.kind,
                        state = excluded.state,
                        display_name = excluded.display_name,
                        last_message = excluded.last_message,
                        attributes = excluded.attributes
                    """;
        }
    }
}
