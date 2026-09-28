package org.jdbscript.liquibase;

import org.jdbscript.MigrationRunner;
import org.jdbscript.MigrationRunnerContractTest;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Test(groups = "migration")
public class LiquibaseMigratorTest extends MigrationRunnerContractTest {

    private static final String CHANGELOG = "db/migration-engine-test-changelog.yaml";

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
