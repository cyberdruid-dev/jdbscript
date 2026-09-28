package org.jdbscript.flyway;

import org.jdbscript.MigrationRunner;
import org.jdbscript.MigrationTestBase;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@Test(groups = "migration")
public class FlywayVersionNormalizationTest extends MigrationTestBase {

    @Override
    protected MigrationRunner createMigrator() {
        return new FlywayMigrator(flywayLocation());
    }

    @Test
    public void migrateTo_accepts_a_version_equivalent_but_differently_formatted_marker() throws Exception {
        // "1.0" and "1" are the same MigrationVersion to Flyway (verified: MigrationVersion
        // .fromVersion("1").equals(MigrationVersion.fromVersion("1.0")) is true) even though their
        // toString() forms differ - migrateTo must accept this, not just an exact string match.
        assertThatCode(() -> createMigrator().migrateTo(dataSource, "1.0"))
                .doesNotThrowAnyException();

        assertThat(columnExists("widget", "description")).isFalse();
    }
}
