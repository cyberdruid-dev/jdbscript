package org.jdbscript;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract every {@link MigrationRunner} implementation must satisfy - run once per tool by a
 * concrete subclass (e.g. {@code LiquibaseMigratorTest}, {@code FlywayMigratorTest}) supplying
 * that tool's own {@link #createMigrator()} and marker values.
 */
public abstract class MigrationRunnerContractTest extends MigrationTestBase {

    protected abstract String markerAfterWidget();

    protected abstract String markerAfterWidgetDescription();

    protected abstract String unknownMarker();

    protected abstract MigrationRunner createMigratorWithInvalidSource();

    protected abstract String invalidSourceIdentifier();

    @Test
    public void migrateTo_stops_exactly_at_the_given_marker() throws Exception {
        createMigrator().migrateTo(dataSource, markerAfterWidget());

        assertThat(columnExists("widget", "description")).isFalse();
    }

    @Test
    public void migrateTo_called_again_with_a_later_marker_applies_only_the_changes_in_between() throws Exception {
        MigrationRunner migrator = createMigrator();

        migrator.migrateTo(dataSource, markerAfterWidget());
        migrator.migrateTo(dataSource, markerAfterWidgetDescription());

        assertThat(columnExists("widget", "description")).isTrue();
    }

    @Test
    public void migrate_runs_every_pending_change() throws Exception {
        createMigrator().migrate(dataSource);

        assertThat(columnExists("widget", "description")).isTrue();
        assertThat(columnExists("migration_person", "full_name")).isTrue();
    }

    @Test
    public void migrateTo_with_unknown_marker_fails_with_a_clear_error() {
        assertThatThrownBy(() -> createMigrator().migrateTo(dataSource, unknownMarker()))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining(unknownMarker());
    }

    @Test
    public void migrateTo_with_invalid_source_fails_with_a_clear_error() {
        assertThatThrownBy(() -> createMigratorWithInvalidSource().migrateTo(dataSource, "any-marker"))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining(invalidSourceIdentifier());
    }

    @Test
    public void reset_drops_everything_the_tool_manages() throws Exception {
        MigrationRunner migrator = createMigrator();
        migrator.migrateTo(dataSource, markerAfterWidget());
        assertThat(tableExists("widget")).isTrue();

        migrator.reset(dataSource);

        assertThat(tableExists("widget")).isFalse();
    }

    @Test
    public void reset_allows_migrations_to_run_again_from_scratch() throws Exception {
        MigrationRunner migrator = createMigrator();
        migrator.migrateTo(dataSource, markerAfterWidget());

        migrator.reset(dataSource);

        migrator.migrateTo(dataSource, markerAfterWidget());
        assertThat(tableExists("widget")).isTrue();
    }

    @Test
    public void reset_on_an_already_blank_datasource_does_not_throw() {
        assertThatCode(() -> createMigrator().reset(dataSource)).doesNotThrowAnyException();
    }

    @Test
    public void reset_wraps_errors_in_JDBScriptException() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:" + getClass().getSimpleName() + "_resetErrors;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("sa");
        HikariDataSource brokenDataSource = new HikariDataSource(config);
        brokenDataSource.close();

        assertThatThrownBy(() -> createMigrator().reset(brokenDataSource))
                .isInstanceOf(JDBScriptException.class);
    }

}
