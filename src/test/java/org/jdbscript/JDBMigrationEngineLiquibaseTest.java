package org.jdbscript;

import org.jdbscript.liquibase.LiquibaseMigrator;
import org.testng.annotations.Test;

@Test(groups = "migration")
public class JDBMigrationEngineLiquibaseTest extends JDBMigrationEngineContractTest {

    private static final String CHANGELOG = "db/migration-engine-test-changelog.yaml";

    @Override
    protected MigrationRunner createMigrator() {
        return new LiquibaseMigrator(CHANGELOG);
    }

    @Override
    protected String markerBeforeFullNameBackfill() {
        return "before-full-name-backfill";
    }

    @Override
    protected String markerAfterFullNameBackfill() {
        return "after-full-name-backfill";
    }

    @Override
    protected String markerBeforeOrderTable() {
        return "before-migration_order-table";
    }

    @Override
    protected String markerAfterOrderTable() {
        return "after-migration_order-table";
    }
}
