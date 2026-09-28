package org.jdbscript;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.errors.JDBScriptException;
import org.jdbscript.impl.conversion.IJDBTypeConverter;
import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract every {@link MigrationRunner}, driven through {@link JDBMigrationEngine}, must satisfy
 * - run once per tool by a concrete subclass supplying that tool's own {@link #createMigrator()}
 * and marker values.
 */
public abstract class JDBMigrationEngineContractTest extends MigrationTestBase {

    protected abstract String markerBeforeFullNameBackfill();

    protected abstract String markerAfterFullNameBackfill();

    protected abstract String markerBeforeOrderTable();

    protected abstract String markerAfterOrderTable();

    /**
     * Used only by the isolated-H2-DataSource test below - that DataSource is always H2 regardless
     * of the active profile, so (unlike {@link #createMigrator()}) this must never resolve to
     * DBMS-specific migration scripts (e.g. Spanner's). Defaults to {@link #createMigrator()},
     * which is already connection-adaptive rather than profile-based for tools like Liquibase.
     */
    protected MigrationRunner createMigratorForIsolatedDatabase() {
        return createMigrator();
    }

    private interface IPersonBeforeSchema extends IDBSchema {
        IPersonBeforeRecord migration_person();

        interface IPersonBeforeRecord extends IDBRecord {
            IPersonBeforeRecord id(int value);
            IPersonBeforeRecord first_name(String value);
            IPersonBeforeRecord last_name(String value);
        }
    }

    private interface IPersonAfterSchema extends IDBSchema {
        IPersonAfterRecord migration_person();

        interface IPersonAfterRecord extends IDBRecord {
            IPersonAfterRecord id(int value);
            IPersonAfterRecord full_name(String value);
        }
    }

    private interface ICustomerOnlySchema extends IDBSchema {
        ICustomerRecord migration_customer();

        interface ICustomerRecord extends IDBRecord {
            ICustomerRecord id(int value);
            ICustomerRecord name(String value);
        }
    }

    private interface ICustomerAndOrderSchema extends IDBSchema {
        ICustomerRecord migration_customer();
        IOrderRecord migration_order();

        interface ICustomerRecord extends IDBRecord {
            ICustomerRecord id(int value);
            ICustomerRecord name(String value);
        }

        interface IOrderRecord extends IDBRecord {
            IOrderRecord id(int value);
            IOrderRecord customer_id(int value);
        }
    }

    private static class ReversingConverter implements IJDBTypeConverter {
        @Override
        public boolean canConvert(Object value) {
            return value instanceof String;
        }

        @Override
        public Object convert(Object value) {
            return new StringBuilder((String) value).reverse().toString();
        }
    }

    private <F extends IDBSchema, A extends IDBSchema> JDBMigrationEngine.Builder<F, A> migrationBuilder(
            Class<F> before, Class<A> after) {
        return JDBMigrationEngine.builder(before, after)
                .dataSource(dataSource)
                .executor(() -> testConfiguration.getScriptExecutor())
                .migrator(createMigrator());
    }

    @Test
    public void backfill_populates_full_name_from_existing_first_and_last_name() {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                migrationBuilder(IPersonBeforeSchema.class, IPersonAfterSchema.class).build();

        migration.migrateTo(markerBeforeFullNameBackfill());
        migration.before().insertDB(db -> db.migration_person().id(1).first_name("Ada").last_name("Lovelace"));

        migration.migrateTo(markerAfterFullNameBackfill());
        migration.after().assertDBHas(db -> db.migration_person().id(1).full_name("Ada Lovelace"));
    }

    @Test
    public void reset_delegates_to_the_migrator_against_the_engines_own_dataSource() throws Exception {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                migrationBuilder(IPersonBeforeSchema.class, IPersonAfterSchema.class).build();

        migration.migrateTo(markerBeforeFullNameBackfill());
        migration.before().insertDB(db -> db.migration_person().id(1).first_name("Ada").last_name("Lovelace"));

        migration.reset();

        assertThat(tableExists("migration_person")).isFalse();

        migration.migrateTo(markerBeforeFullNameBackfill());
        assertThat(tableExists("migration_person")).isTrue();
    }

    @Test
    public void reset_works_before_any_migrateTo_call() {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                migrationBuilder(IPersonBeforeSchema.class, IPersonAfterSchema.class).build();

        assertThatCode(migration::reset).doesNotThrowAnyException();
    }

    @Test
    public void migrateTo_never_implicitly_resets() {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                migrationBuilder(IPersonBeforeSchema.class, IPersonAfterSchema.class).build();

        migration.migrateTo(markerBeforeFullNameBackfill());
        migration.before().insertDB(db -> db.migration_person().id(1).first_name("Ada").last_name("Lovelace"));

        migration.migrateTo(markerAfterFullNameBackfill());

        migration.after().assertDBHas(db -> db.migration_person().id(1).full_name("Ada Lovelace"));
    }

    @Test
    public void before_and_after_share_the_same_dataSource() throws Exception {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                migrationBuilder(IPersonBeforeSchema.class, IPersonAfterSchema.class).build();

        migration.migrateTo(markerBeforeFullNameBackfill());
        migration.before().insertDB(db -> db.migration_person().id(1).first_name("Ada").last_name("Lovelace"));

        try (Connection cnn = dataSource.getConnection();
             ResultSet rs = cnn.createStatement().executeQuery("SELECT first_name FROM migration_person WHERE id = 1")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("first_name")).isEqualTo("Ada");
        }
    }

    @Test
    public void before_and_after_are_memoized() {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                migrationBuilder(IPersonBeforeSchema.class, IPersonAfterSchema.class).build();

        assertThat(migration.before()).isSameAs(migration.before());
        assertThat(migration.after()).isSameAs(migration.after());
    }

    @Test
    public void default_settings_pick_up_a_table_added_by_the_migration() {
        JDBMigrationEngine<ICustomerOnlySchema, ICustomerAndOrderSchema> migration =
                migrationBuilder(ICustomerOnlySchema.class, ICustomerAndOrderSchema.class).build();

        migration.migrateTo(markerBeforeOrderTable());
        migration.before().insertDB(db -> db.migration_customer().id(1).name("Acme"));

        migration.migrateTo(markerAfterOrderTable());
        migration.after().insertDB(db -> {
            db.migration_customer().id(2).name("Beta");
            db.migration_order().id(100).customer_id(2);
        });

        migration.after().assertDBHas(db -> db.migration_order().id(100).customer_id(2));
    }

    @Test
    public void forcing_cacheStrategy_GLOBAL_via_the_escape_hatch_can_see_stale_metadata_across_the_migration() {
        // CacheStrategy.GLOBAL's cache is a JVM-wide singleton keyed by connection identity
        // (JDBCacheManager) - the rest of the suite's engines default to GLOBAL too (see
        // JdbAbstractTest.engineBuilder), so proving staleness here needs a datasource with its
        // own connection identity, not the shared one every other test's cache is also keyed by.
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + "_globalCacheDemo;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        try (HikariDataSource isolatedDataSource = new HikariDataSource(config)) {
            JDBMigrationEngine<ICustomerOnlySchema, ICustomerAndOrderSchema> migration = JDBMigrationEngine
                    .builder(ICustomerOnlySchema.class, ICustomerAndOrderSchema.class)
                    .dataSource(isolatedDataSource)
                    .executor(() -> testConfiguration.getScriptExecutor())
                    .migrator(createMigratorForIsolatedDatabase())
                    .beforeEngine(b -> b.cacheStrategy(CacheStrategy.GLOBAL))
                    .afterEngine(b -> b.cacheStrategy(CacheStrategy.GLOBAL))
                    .build();

            migration.migrateTo(markerBeforeOrderTable());
            migration.before().insertDB(db -> db.migration_customer().id(1).name("Acme"));

            migration.migrateTo(markerAfterOrderTable());

            assertThatThrownBy(() -> migration.after().insertDB(db -> {
                db.migration_customer().id(2).name("Beta");
                db.migration_order().id(100).customer_id(2);
            })).isInstanceOf(JDBScriptException.class);
        }
    }

    @Test
    public void shared_converter_applies_to_both_before_and_after() throws Exception {
        JDBMigrationEngine<IPersonBeforeSchema, IPersonAfterSchema> migration =
                migrationBuilder(IPersonBeforeSchema.class, IPersonAfterSchema.class)
                        .converter(new ReversingConverter())
                        .build();

        migration.migrateTo(markerBeforeFullNameBackfill());
        migration.before().insertDB(db -> db.migration_person().id(1).first_name("Ada").last_name("Lovelace"));

        migration.migrateTo(markerAfterFullNameBackfill());
        migration.after().insertDB(db -> db.migration_person().id(2).full_name("Beta"));

        assertThat(queryString("SELECT first_name FROM migration_person WHERE id = 1")).isEqualTo("adA");
        assertThat(queryString("SELECT full_name FROM migration_person WHERE id = 2")).isEqualTo("ateB");
    }


    private String queryString(String sql) throws Exception {
        try (Connection cnn = dataSource.getConnection();
             ResultSet rs = cnn.createStatement().executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
