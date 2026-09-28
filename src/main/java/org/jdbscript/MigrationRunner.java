package org.jdbscript;

import javax.sql.DataSource;

/**
 * Strategy interface for running a project's database migrations up to an addressable stopping
 * point - just enough for {@link JDBMigrationEngine} to seed data at a specific pre-migration
 * state, then resume past the migration under test. Each implementation wraps one migration
 * tool's own notion of an addressable point (e.g. a Liquibase tag, a Flyway version); this
 * interface doesn't try to unify those concepts beyond a plain {@code String} marker.
 * <p>
 * Not a general-purpose migration API - if you need more than this, use the underlying migration
 * tool's own API directly.
 */
public interface MigrationRunner {

    /**
     * Runs the migration up to and including the given marker, then stops - even if more
     * migrations are pending beyond it.
     *
     * @param dataSource the target database
     * @param marker the tool-specific stopping point (e.g. a Liquibase tag)
     */
    void migrateTo(DataSource dataSource, String marker);

    /**
     * Runs every pending migration, with no stopping point.
     *
     * @param dataSource the target database
     */
    void migrate(DataSource dataSource);

    /**
     * Wipes everything this migration tool manages on the target database - never invoked
     * automatically by {@link #migrateTo}/{@link #migrate}, an explicit opt-in for getting a
     * datasource back to blank before testing a migration from scratch.
     *
     * @param dataSource the target database
     */
    void reset(DataSource dataSource);
}
