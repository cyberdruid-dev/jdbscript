package org.jdbscript.flyway;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses its own throwaway H2 DataSource rather than the shared profile-based one other migration
 * tests use, since this is only about which location strings resolve, not migrating anything.
 */
@Test
public class FlywayMigratorLocationTest {

    @Test
    public void migrate_should_accept_a_classpath_location_with_a_leading_slash() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + "_leadingSlash;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            FlywayMigrator migrator = new FlywayMigrator("classpath:/db/flyway-migration");

            assertThatCode(() -> migrator.migrate(dataSource))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    public void migrate_should_accept_a_filesystem_location() throws Exception {
        URL resource = Thread.currentThread().getContextClassLoader().getResource("db/flyway-migration");
        String path = Paths.get(resource.toURI()).toString();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + "_filesystem;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        try (HikariDataSource dataSource = new HikariDataSource(config)) {
            FlywayMigrator migrator = new FlywayMigrator("filesystem:" + path);

            assertThatCode(() -> migrator.migrate(dataSource))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    public void checkLocationResolvable_should_not_throw_when_the_thread_has_no_context_classloader() throws Exception {
        // Exercises just the pre-check, not the full migrate() call: Flyway's own internals (deeper
        // in load()) have this exact same null-context-classloader dependency, which is Flyway's
        // problem, not jdbscript's - out of scope here.
        FlywayMigrator migrator = new FlywayMigrator("classpath:db/flyway-migration");
        Method checkLocationResolvable = FlywayMigrator.class.getDeclaredMethod("checkLocationResolvable", String.class);
        checkLocationResolvable.setAccessible(true);

        ClassLoader originalContext = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            assertThatCode(() -> checkLocationResolvable.invoke(migrator, "classpath:db/flyway-migration"))
                    .doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContext);
        }
    }

    @Test
    public void migrate_should_reject_a_location_with_an_unrecognized_prefix() {
        FlywayMigrator migrator = new FlywayMigrator("s3:some-bucket");

        assertThatThrownBy(() -> migrator.migrate(null))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("s3:some-bucket");
    }
}
