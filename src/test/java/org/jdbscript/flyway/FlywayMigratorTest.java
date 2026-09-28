package org.jdbscript.flyway;

import org.jdbscript.MigrationRunner;
import org.jdbscript.MigrationRunnerContractTest;
import org.testng.annotations.Test;

@Test(groups = "migration")
public class FlywayMigratorTest extends MigrationRunnerContractTest {

    private static final String LOCATION = "classpath:db/flyway-migration";

    @Override
    protected MigrationRunner createMigrator() {
        return new FlywayMigrator(LOCATION);
    }

    @Override
    protected MigrationRunner createMigratorWithInvalidSource() {
        return new FlywayMigrator("classpath:db/no-such-flyway-location");
    }

    @Override
    protected String invalidSourceIdentifier() {
        return "db/no-such-flyway-location";
    }

    @Override
    protected String markerAfterWidget() {
        return "1";
    }

    @Override
    protected String markerAfterWidgetDescription() {
        return "2";
    }

    @Override
    protected String unknownMarker() {
        return "999";
    }
}
