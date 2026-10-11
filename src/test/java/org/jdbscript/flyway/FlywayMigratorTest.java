package org.jdbscript.flyway;

import org.jdbscript.MigrationRunner;
import org.jdbscript.MigrationRunnerContractTest;
import org.jdbscript.errors.JDBScriptException;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Test(groups = "migration")
public class FlywayMigratorTest extends MigrationRunnerContractTest {

    @Override
    protected MigrationRunner createMigrator() {
        return new FlywayMigrator(flywayLocation());
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

    @Test
    public void migrateTo_with_a_non_numeric_version_fails_with_a_clear_error() {
        assertThatThrownBy(() -> createMigrator().migrateTo(dataSource, "after-widget"))
                .isInstanceOf(JDBScriptException.class)
                .hasMessageContaining("after-widget");
    }

    @Override
    protected String markerCreateWidget() {
        return "1";
    }

    @Override
    protected String markerAddWidgetDescription() {
        return "2";
    }
}
