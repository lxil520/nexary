package org.nexary.governance.platform.storage.postgres;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.nexary.governance.platform.storage.jdbc.GovernancePlatformJdbcDialects;
import org.nexary.governance.platform.storage.jdbc.JdbcGovernancePlatformRepository;
import org.springframework.jdbc.core.JdbcOperations;

/** Compatibility repository that pins governance platform JDBC storage to PostgreSQL. */
public final class PostgresGovernancePlatformRepository extends JdbcGovernancePlatformRepository {
    /** Creates the repository and initializes required PostgreSQL tables. */
    public PostgresGovernancePlatformRepository(JdbcOperations jdbcOperations, ObjectMapper objectMapper) {
        super(jdbcOperations, objectMapper, GovernancePlatformJdbcDialects.postgres());
    }
}
