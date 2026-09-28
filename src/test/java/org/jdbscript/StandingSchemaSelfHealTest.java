package org.jdbscript;

import org.jdbscript.liquibase.LiquibaseMigrator;
import org.testng.annotations.Test;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@Test(groups = "migration")
public class StandingSchemaSelfHealTest extends MigrationTestBase {

    private static final String CHANGELOG = "db/migration-engine-test-changelog.yaml";

    @Override
    protected MigrationRunner createMigrator() {
        return new LiquibaseMigrator(CHANGELOG);
    }

    @Test
    public void ensureStandingSchemaExists_rebuilds_a_missing_standing_table() throws Exception {
        // MigrationTestBase's own @BeforeMethod already reset() the shared schema before this
        // test body ran - table_1 is already gone, exactly the precondition this checks.
        assertThat(columnCount("table_1")).isZero();

        ensureStandingSchemaExists();

        assertThat(columnCount("table_1")).isGreaterThan(0);
    }

    private int columnCount(String table) throws Exception {
        try (Connection cnn = dataSource.getConnection();
             var rs = cnn.getMetaData().getColumns(null, null, "%", "%")) {
            int count = 0;
            while (rs.next()) {
                if (rs.getString("TABLE_NAME").equalsIgnoreCase(table)) {
                    count++;
                }
            }
            return count;
        }
    }
}
