package org.nexary.governance.platform.storage.jdbc;

/** SQL boundary for supported governance platform JDBC databases. */
public interface GovernancePlatformJdbcDialect {
    /** Returns the stable dialect name. */
    String name();

    /** Returns the SQL fragment for the signal id primary key column. */
    String signalIdColumn();

    /** Returns the upsert SQL for platform assets. */
    String upsertAssetSql();

    /** Returns the upsert SQL for platform dependencies. */
    String upsertDependencySql();

    /** Returns the upsert SQL for platform connectors. */
    String upsertConnectorSql();
}
