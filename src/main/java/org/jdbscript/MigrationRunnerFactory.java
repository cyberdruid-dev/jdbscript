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
        // Class.forName(name) alone resolves via the calling class's own loader - in a setup where
        // jdbscript and the migration tool sit on different classloaders (e.g. an app server with
        // jdbscript on a shared/parent classloader), that misses a Flyway/Liquibase genuinely
        // reachable via the current thread's context classloader, so fall back to that too.
        return classIsLoadableBy(className, MigrationRunnerFactory.class.getClassLoader())
                || classIsLoadableBy(className, Thread.currentThread().getContextClassLoader());
    }

    private static boolean classIsLoadableBy(String className, ClassLoader loader) {
        if (loader == null) {
            return false;
        }
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
