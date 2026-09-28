package org.jdbscript;

import org.jdbscript.flyway.FlywayMigrator;
import org.testng.annotations.Test;

@Test(groups = "migration")
public class JDBMigrationEngineFlywayTest extends JDBMigrationEngineContractTest {

    @Override
    protected MigrationRunner createMigrator() {
        return new FlywayMigrator(flywayLocation());
    }

    @Override
    protected MigrationRunner createMigratorForIsolatedDatabase() {
        return new FlywayMigrator(FLYWAY_LOCATION);
    }

    @Override
    protected String markerBeforeFullNameBackfill() {
        return "3";
    }

    @Override
    protected String markerAfterFullNameBackfill() {
        return "5";
    }

    @Override
    protected String markerBeforeOrderTable() {
        return "6";
    }

    @Override
    protected String markerAfterOrderTable() {
        return "7";
    }
}
