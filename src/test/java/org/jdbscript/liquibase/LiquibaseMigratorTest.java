package org.jdbscript.liquibase;

import org.jdbscript.MigrationRunner;
import org.jdbscript.MigrationRunnerContractTest;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test(groups = "migration")
public class LiquibaseMigratorTest extends MigrationRunnerContractTest {

    private static final String CHANGELOG = "db/migration-engine-test-changelog.yaml";
    private static final String MARKER_RESOLUTION_CHANGELOG = "db/marker-resolution-changelog.yaml";

    @Override
    protected MigrationRunner createMigrator() {
        return new LiquibaseMigrator(CHANGELOG);
    }

    @Override
    protected MigrationRunner createMigratorWithInvalidSource() {
        return new LiquibaseMigrator("db/no-such-changelog.yaml");
    }

    @Override
    protected String invalidSourceIdentifier() {
        return "no-such-changelog.yaml";
    }

    @Override
    protected String markerAfterWidget() {
        return "after-widget";
    }

    @Override
    protected String markerAfterWidgetDescription() {
        return "after-widget-description";
    }

    @Override
    protected String unknownMarker() {
        return "no-such-tag";
    }

    @Override
    protected String markerCreateWidget() {
        return "001-create-widget";
    }

    @Override
    protected String markerAddWidgetDescription() {
        return "003-add-widget-description";
    }

    @Test
    public void migrateBefore_a_tag_stops_right_before_its_tagDatabase_changeset() throws Exception {
        LiquibaseMigrator migrator = new LiquibaseMigrator(CHANGELOG);

        migrator.migrateBefore(dataSource, "after-widget-description");
        assertThat(columnExists("widget", "description")).isTrue();

        migrator.migrateTo(dataSource, "after-widget-description");
        assertThat(tableExists("migration_person")).isFalse();
    }

    @Test
    public void marker_matching_both_a_tag_and_a_changeset_id_fails_with_a_clear_error() {
        LiquibaseMigrator migrator = new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG);

        assertThatThrownBy(() -> migrator.migrateTo(dataSource, "add-description"))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("add-description")
                .hasMessageContaining("tag:add-description")
                .hasMessageContaining("changeset:add-description");
    }

    @Test
    public void tag_prefix_resolves_only_tags() throws Exception {
        new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "tag:add-description");

        assertThat(tableExists("widget")).isTrue();
        assertThat(columnExists("widget", "description")).isFalse();
    }

    @Test
    public void changeset_prefix_resolves_only_changeset_ids() throws Exception {
        new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "changeset:add-description");

        assertThat(columnExists("widget", "description")).isTrue();
    }

    @Test
    public void tag_prefix_does_not_fall_back_to_changeset_ids() throws Exception {
        assertThatThrownBy(() -> new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "tag:create-widget"))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("create-widget");

        assertThat(tableExists("widget")).isFalse();
    }

    @Test
    public void changeset_id_shared_by_several_changesets_fails_with_a_clear_error() throws Exception {
        assertThatThrownBy(() -> new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "add-column"))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("ambiguous")
                .hasMessageContaining("add-column::alice")
                .hasMessageContaining("add-column::bob");

        assertThat(tableExists("widget")).isFalse();
    }

    @Test
    public void id_and_author_select_one_of_the_changesets_sharing_an_id() throws Exception {
        new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "add-column::bob");

        assertThat(columnExists("widget", "beta")).isTrue();
        assertThat(columnExists("widget", "gamma")).isFalse();
    }

    @Test
    public void id_and_author_shared_by_changesets_in_different_files_fails_with_a_clear_error() {
        assertThatThrownBy(() -> new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "add-column::alice"))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("ambiguous")
                .hasMessageContaining("marker-resolution-changelog.yaml::add-column::alice")
                .hasMessageContaining("marker-resolution-included.yaml::add-column::alice");
    }

    @Test
    public void path_id_and_author_select_one_of_the_changesets_in_different_files() throws Exception {
        new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG)
                .migrateTo(dataSource, "db/marker-resolution-included.yaml::add-column::alice");

        assertThat(columnExists("widget", "gamma")).isTrue();
        assertThat(columnExists("widget", "delta")).isFalse();
    }

    @Test
    public void changeset_prefix_accepts_id_and_author() throws Exception {
        new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "changeset:add-column::bob");

        assertThat(columnExists("widget", "beta")).isTrue();
        assertThat(columnExists("widget", "gamma")).isFalse();
    }

    @Test
    public void changeset_in_an_included_changelog_is_found() throws Exception {
        new LiquibaseMigrator(MARKER_RESOLUTION_CHANGELOG).migrateTo(dataSource, "only-in-included");

        assertThat(columnExists("widget", "delta")).isTrue();
    }

    @Test
    public void migrate_should_work_when_the_thread_has_no_context_classloader() throws Exception {
        ClassLoader originalContext = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            createMigrator().migrate(dataSource);
        } finally {
            Thread.currentThread().setContextClassLoader(originalContext);
        }

        assertThat(columnExists("widget", "description")).isTrue();
    }
}
