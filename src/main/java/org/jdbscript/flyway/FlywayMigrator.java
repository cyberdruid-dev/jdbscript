package org.jdbscript.flyway;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.Location;
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
        // migrate() silently no-ops if marker is already behind the current version, so check
        // explicitly. Compare parsed versions, not raw strings: "1" and "1.0" are equal to Flyway.
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
        // Check before dataSource() is set: Flyway's own missing-location check happens deep inside
        // migrate()/clean(), after it has already opened (and, in 12.11.0-13.8.0, leaked) a connection.
        checkLocationResolvable(location);
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .failOnMissingLocations(true);
    }

    private void checkLocationResolvable(String location) {
        Location parsed;
        try {
            parsed = new Location(location);
        } catch (FlywayException e) {
            throw new JDBScriptException(
                    "Flyway migration failed (location '" + location + "'): " + e.getMessage(), e);
        }
        boolean resolvable;
        if (parsed.isClassPath()) {
            resolvable = Thread.currentThread().getContextClassLoader().getResource(parsed.getPath()) != null;
        } else if (parsed.isFileSystem()) {
            resolvable = Files.isDirectory(Paths.get(parsed.getPath()));
        } else {
            resolvable = true;
        }
        if (!resolvable) {
            throw new JDBScriptException("Flyway migration failed (location '" + location + "'): "
                    + "Unable to resolve location " + location);
        }
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
