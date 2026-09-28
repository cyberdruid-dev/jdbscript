package org.jdbscript.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jdbscript.MigrationRunner;
import org.jdbscript.errors.JDBScriptException;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * {@link MigrationRunner} backed by Flyway. {@code marker} in {@link #migrateTo} is a Flyway
 * version (e.g. {@code "3"} for {@code V3__some_description.sql}) - migrations are already
 * individually versioned, so no separate tagging convention is needed the way Liquibase's tags
 * are.
 */
public class FlywayMigrator implements MigrationRunner {

    private final String location;

    /**
     * @param location a Flyway migration location (e.g. {@code "classpath:db/migration"})
     */
    public FlywayMigrator(String location) {
        this.location = location;
    }

    @Override
    public void migrateTo(DataSource dataSource, String marker) {
        Flyway flyway = configure(dataSource).target(marker).load();
        run(flyway::migrate);
        MigrationInfo current = flyway.info().current();
        // Flyway itself rejects a target matching no migration at all, but not one that's already
        // behind the currently applied version (nothing pending, migrate() just no-ops) - this
        // catches that case too. Compares parsed MigrationVersions, not raw strings: "1" and "1.0"
        // are the same version to Flyway but format differently via toString().
        if (current == null || !MigrationVersion.fromVersion(marker).equals(current.getVersion())) {
            throw new JDBScriptException(
                    "Flyway version '" + marker + "' not found in location '" + location + "'.");
        }
    }

    @Override
    public void migrate(DataSource dataSource) {
        run(configure(dataSource).load()::migrate);
    }

    @Override
    public void reset(DataSource dataSource) {
        run(configure(dataSource).cleanDisabled(false).load()::clean);
    }

    private FluentConfiguration configure(DataSource dataSource) {
        // Flyway's own failOnMissingLocations(true) check happens deep inside migrate()/clean(),
        // by which point it has already opened (and, at least in 12.11.0 through 13.8.0, leaked) a
        // connection - checked directly upstream, not via Flyway's own bug tracker. Checking here,
        // before dataSource() is ever set, means Flyway never gets a connection to leak for this
        // case. Locations Flyway resolves some other way (not classpath:/filesystem:) are left to
        // Flyway's own handling rather than risk this check rejecting one incorrectly.
        if (!locationExists(location)) {
            throw new JDBScriptException("Flyway migration failed (location '" + location + "'): "
                    + "Unable to resolve location " + location);
        }
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .failOnMissingLocations(true);
    }

    private boolean locationExists(String location) {
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            return Thread.currentThread().getContextClassLoader().getResource(path) != null;
        }
        if (location.startsWith("filesystem:")) {
            return Files.isDirectory(Paths.get(location.substring("filesystem:".length())));
        }
        return true;
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (FlywayException e) {
            throw new JDBScriptException(
                    "Flyway migration failed (location '" + location + "'): " + e.getMessage(), e);
        }
    }
}
