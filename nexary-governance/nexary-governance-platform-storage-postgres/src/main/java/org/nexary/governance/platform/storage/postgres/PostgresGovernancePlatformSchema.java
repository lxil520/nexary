package org.nexary.governance.platform.storage.postgres;

import org.nexary.governance.platform.storage.jdbc.GovernancePlatformJdbcDialects;
import org.nexary.governance.platform.storage.jdbc.JdbcGovernancePlatformSchema;
import org.springframework.jdbc.core.JdbcOperations;

import java.util.Objects;

/** Compatibility schema helper that pins governance platform JDBC storage to PostgreSQL. */
public final class PostgresGovernancePlatformSchema {
    private PostgresGovernancePlatformSchema() {
    }

    /** Creates platform storage tables when they do not exist. */
    public static void initialize(JdbcOperations jdbcOperations) {
        Objects.requireNonNull(jdbcOperations, "jdbcOperations");
        JdbcGovernancePlatformSchema.initialize(jdbcOperations, GovernancePlatformJdbcDialects.postgres());
    }
}
