package org.jdbscript;

import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.flyway.FlywayMigrator;
import org.jdbscript.liquibase.LiquibaseMigrator;

/**
 * Picks a {@link MigrationRunner} based on which migration tool's classes are present on the
 * classpath - see {@link JDBMigrationEngine.Builder#migrations}.
 */
final class MigrationRunnerFactory {

    private MigrationRunnerFactory() {
    }

    static MigrationRunner detect(String path) {
        return decide(path, classExists("liquibase.Liquibase"), classExists("org.flywaydb.core.Flyway"));
    }

    static MigrationRunner decide(String path, boolean hasLiquibase, boolean hasFlyway) {
        if (hasLiquibase && hasFlyway) {
            throw new JDBScriptException(
                    "Both Liquibase and Flyway are on the classpath - can't auto-detect which one to "
                            + "use. Pass a MigrationRunner directly instead: .migrator(new "
                            + "LiquibaseMigrator(...)) or .migrator(new FlywayMigrator(...)).");
        }
        if (hasLiquibase) {
            return new LiquibaseMigrator(path);
        }
        if (hasFlyway) {
            return new FlywayMigrator(path);
        }
        throw new JDBScriptException(
                "Neither Liquibase nor Flyway is on the classpath - can't auto-detect a migration tool. "
                        + "Pass a MigrationRunner directly instead: .migrator(...).");
    }

    static boolean classExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
