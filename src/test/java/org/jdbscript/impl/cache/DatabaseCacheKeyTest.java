package org.jdbscript.impl.cache;

import org.testng.annotations.Test;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.assertThat;

public class DatabaseCacheKeyTest extends AbstractCacheTest {

    @Test
    public void sanitizeUrl_strips_h2_parameters() {
        assertThat(DatabaseCacheKey.sanitizeUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")).isEqualTo("jdbc:h2:mem:test");
        assertThat(DatabaseCacheKey.sanitizeUrl("jdbc:h2:mem:test;TRACE_LEVEL_FILE=4;DB_CLOSE_DELAY=-1")).isEqualTo("jdbc:h2:mem:test");
    }

    @Test
    public void sanitizeUrl_strips_postgres_and_mysql_parameters() {
        assertThat(DatabaseCacheKey.sanitizeUrl("jdbc:postgresql://localhost:5432/db?ssl=true")).isEqualTo("jdbc:postgresql://localhost:5432/db");
        assertThat(DatabaseCacheKey.sanitizeUrl("jdbc:mysql://localhost:3306/db?useSSL=false&serverTimezone=UTC")).isEqualTo("jdbc:mysql://localhost:3306/db");
    }

    @Test
    public void sanitizeUrl_normalizes_parameter_order_by_stripping() {
        String url1 = "jdbc:postgresql://localhost:5432/db?param1=a&param2=b";
        String url2 = "jdbc:postgresql://localhost:5432/db?param2=b&param1=a";
        assertThat(DatabaseCacheKey.sanitizeUrl(url1)).isEqualTo(DatabaseCacheKey.sanitizeUrl(url2));
    }

    @Test
    public void sanitizeUrl_handles_null_and_empty() {
        assertThat(DatabaseCacheKey.sanitizeUrl(null)).isEqualTo("");
        assertThat(DatabaseCacheKey.sanitizeUrl("  ")).isEqualTo("");
    }

    @Test
    public void from_connection_normalizes_identity() throws SQLException {
        DatabaseCacheKey key1 = DatabaseCacheKey.from(mockConnection("jdbc:h2:mem:test;P1=V1", "sa", "PUBLIC"));
        DatabaseCacheKey key2 = DatabaseCacheKey.from(mockConnection("jdbc:h2:mem:test;P2=V2", "sa", "PUBLIC"));

        assertThat(key1).isEqualTo(key2);
        assertThat(key1.jdbcUrl()).isEqualTo("jdbc:h2:mem:test");
    }

    @Test
    public void keys_are_different_for_different_users() throws SQLException {
        DatabaseCacheKey key1 = DatabaseCacheKey.from(mockConnection("jdbc:h2:mem:test", "user1", "PUBLIC"));
        DatabaseCacheKey key2 = DatabaseCacheKey.from(mockConnection("jdbc:h2:mem:test", "user2", "PUBLIC"));

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    public void keys_are_different_for_different_schemas() throws SQLException {
        DatabaseCacheKey key1 = DatabaseCacheKey.from(mockConnection("jdbc:h2:mem:test", "sa", "SCHEMA1"));
        DatabaseCacheKey key2 = DatabaseCacheKey.from(mockConnection("jdbc:h2:mem:test", "sa", "SCHEMA2"));

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    public void keys_are_different_for_same_catalog_but_different_schema() throws SQLException {
        // Regression test: for DBMSes where getCatalog() is non-blank (e.g. Postgres reporting the
        // database name), the schema/search_path must still distinguish tenants sharing that
        // catalog - otherwise CacheStrategy.GLOBAL would hand out the same cache to both.
        DatabaseCacheKey key1 = DatabaseCacheKey.from(mockConnection("jdbc:postgresql://localhost/db", "sa", "db", "tenant1"));
        DatabaseCacheKey key2 = DatabaseCacheKey.from(mockConnection("jdbc:postgresql://localhost/db", "sa", "db", "tenant2"));

        assertThat(key1).isNotEqualTo(key2);
    }
}
