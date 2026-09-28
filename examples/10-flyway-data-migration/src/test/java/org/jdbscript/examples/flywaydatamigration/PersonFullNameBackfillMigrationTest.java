package org.jdbscript.examples.flywaydatamigration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBMigrationEngine;
import org.jdbscript.flyway.FlywayMigrator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Demonstrates testing what a migration does to existing data, not just that it applies without
 * error - the harder case Flyway's own tooling doesn't cover. {@code V3__backfill_full_name.sql}
 * models a realistic case: it backfills a new {@code full_name} column from
 * {@code first_name}/{@code last_name} on rows that already exist.
 * <p>
 * To test that, jdbscript needs to seed rows in the PRE-migration shape - hence
 * {@link IPersonBeforeSchema}, narrower than {@link IPersonAfterSchema} used for the assertion.
 * {@code JDBMigrationEngine} gets the database to that exact in-between state: Flyway migrations
 * are already individually versioned, so the version number of the migration right before/after
 * the one under test *is* the marker - no separate tagging convention needed.
 */
class PersonFullNameBackfillMigrationTest {

    private static final String LOCATION = "classpath:db/migration";
    private static final String VERSION_BEFORE_BACKFILL = "1";
    private static final String VERSION_AFTER_BACKFILL = "3";

    private interface IPersonBeforeSchema extends IDBSchema {
        IPersonBeforeRecord person();

        interface IPersonBeforeRecord extends IDBRecord {
            IPersonBeforeRecord id(int value);
            IPersonBeforeRecord first_name(String value);
            IPersonBeforeRecord last_name(String value);
        }
    }

    private interface IPersonAfterSchema extends IDBSchema {
        IPersonAfterRecord person();

        interface IPersonAfterRecord extends IDBRecord {
            IPersonAfterRecord id(int value);
            IPersonAfterRecord full_name(String value);
        }
    }

    private static HikariDataSource dataSource;

    @BeforeAll
    static void createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:flyway_data_migration;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        dataSource = new HikariDataSource(config);
    }

    @AfterAll
    static void closeDataSource() {
        dataSource.close();
    }

    @Test
    void backfill_populates_full_name_from_existing_first_and_last_name() {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                createMigrationEngine(IPersonBeforeSchema.class, IPersonAfterSchema.class);

        // Arrange: bring the DB to the state right before the migration under test.
        migration.migrateTo(VERSION_BEFORE_BACKFILL);
        migration.before().insertDB(db -> {
            db.person().id(1).first_name("Ada").last_name("Lovelace");
            db.person().id(2).first_name("Alan").last_name("Turing");
        });

        // Act: resume to the version right after the backfill under test - not "everything else".
        migration.migrateTo(VERSION_AFTER_BACKFILL);

        // Assert: via the post-migration shape - full_name should be derived from the old rows,
        // not just present as a new empty column.
        migration.after().assertDBHas(db -> db.person().id(1).full_name("Ada Lovelace"));
        migration.after().assertDBHas(db -> db.person().id(2).full_name("Alan Turing"));
    }

    private static <F extends IDBSchema, A extends IDBSchema> JDBMigrationEngine<F, A> createMigrationEngine(
            Class<F> beforeSchema, Class<A> afterSchema) {
        return JDBMigrationEngine.builder(beforeSchema, afterSchema)
                .dataSource(dataSource)
                .migrator(new FlywayMigrator(LOCATION))
                .build();
    }
}
