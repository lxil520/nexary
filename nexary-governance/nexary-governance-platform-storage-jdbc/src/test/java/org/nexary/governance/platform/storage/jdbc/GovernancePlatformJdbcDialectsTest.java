package org.nexary.governance.platform.storage.jdbc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GovernancePlatformJdbcDialectsTest {
    @Test
    void resolvesSupportedDatabaseNames() {
        assertThat(GovernancePlatformJdbcDialects.resolve("PostgreSQL").name()).isEqualTo("postgres");
        assertThat(GovernancePlatformJdbcDialects.resolve("MySQL").name()).isEqualTo("mysql");
        assertThat(GovernancePlatformJdbcDialects.resolve("MariaDB").name()).isEqualTo("mysql");
        assertThat(GovernancePlatformJdbcDialects.resolve("SQLite").name()).isEqualTo("sqlite");
    }

    @Test
    void dialectsOwnVendorSpecificUpsertSyntax() {
        assertThat(GovernancePlatformJdbcDialects.postgres().upsertAssetSql()).contains("on conflict");
        assertThat(GovernancePlatformJdbcDialects.mysql().upsertAssetSql()).contains("on duplicate key update");
        assertThat(GovernancePlatformJdbcDialects.sqlite().signalIdColumn()).contains("autoincrement");
    }
}
