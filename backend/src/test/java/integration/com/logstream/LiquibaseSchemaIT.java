package integration.com.logstream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

public class LiquibaseSchemaIT extends PostgresBaseIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseAppliesAuthUserColumnsAndIndexInPostgres() {
        assertThat(columnExists("users", "role")).isTrue();
        assertThat(columnExists("users", "auth_provider")).isTrue();
        assertThat(columnExists("users", "auth_subject")).isTrue();
        assertThat(indexExists("uk_users_auth_provider_subject")).isTrue();
    }

    private Boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName);
        return count != null && count == 1;
    }

    private Boolean indexExists(String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = ?
                """,
                Integer.class,
                indexName);
        return count != null && count == 1;
    }
}
