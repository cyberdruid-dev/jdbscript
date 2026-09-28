package org.jdbscript;

import org.jdbscript.errors.JDBScriptException;
import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeMethod;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * Shared fixture for tests that deliberately wipe the shared standing schema (via
 * {@code reset()}) to test a migration from a blank slate. Runs against the same
 * {@link JdbAbstractTest#dataSource} as every other test - no dedicated per-class datasource, no
 * DBMS restriction - so it's exercised by every supported DBMS profile, same as the rest of the
 * suite.
 * <p>
 * Not tied to one migration tool - subclasses supply their own {@link #createMigrator()}.
 * Subclasses must also tag their own {@code @Test(groups = "migration")} at the class level (not
 * inherited here, to avoid relying on TestNG's class-level {@code @Test} annotation-inheritance
 * semantics across an abstract base).
 */
public abstract class MigrationTestBase extends JdbAbstractTest {

    protected static final String FLYWAY_LOCATION = "classpath:db/flyway-migration";
    private static final String FLYWAY_LOCATION_SPANNER = "classpath:db/flyway-migration-spanner";

    protected abstract MigrationRunner createMigrator();

    protected static String flywayLocation() {
        return testConfiguration.getDbmsType() == DBMSType.SPANNER ? FLYWAY_LOCATION_SPANNER : FLYWAY_LOCATION;
    }

    @BeforeMethod
    public void resetMigrationSchema() {
        // Neither migration tool works against DuckDB (see DuckdbSchemaInitStrategy). Skipped
        // unconditionally rather than reactively like the check below - Liquibase's reset()
        // doesn't actually throw here, it swallows the failure and leaves the schema stale.
        skipFor("Migration tools", DBMSType.DUCKDB);
        try {
            createMigrator().reset(dataSource);
        } catch (JDBScriptException e) {
            if (migrationToolDoesNotSupportThisDbms(e)) {
                throw new SkipException("Migration tool does not support this DBMS: " + e.getMessage(), e);
            }
            throw e;
        }
    }

    private boolean migrationToolDoesNotSupportThisDbms(JDBScriptException e) {
        // Flyway Community only recognizes a fixed, hardcoded list of database types (each
        // needing its own flyway-<db> module beyond flyway-core) - confirmed via
        // "Unsupported Database: ...DB2..." against that profile.
        return e.getMessage() != null && e.getMessage().contains("Unsupported Database");
    }

    /**
     * Runs exactly once, after every method in the whole {@code migration} group (across every
     * class that belongs to it) has finished - restores the standing schema these tests wiped,
     * for whatever runs next.
     */
    @AfterGroups(groups = "migration", alwaysRun = true)
    public static void restoreStandingSchema() {
        testConfiguration.reinitStandingSchema(testConfiguration.getDataSource());
    }

    protected boolean tableExists(String table) throws Exception {
        try (Connection cnn = dataSource.getConnection();
             ResultSet rs = cnn.getMetaData().getTables(null, null, "%", null)) {
            while (rs.next()) {
                if (rs.getString("TABLE_NAME").equalsIgnoreCase(table)) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean columnExists(String table, String column) throws Exception {
        try (Connection cnn = dataSource.getConnection()) {
            DatabaseMetaData metaData = cnn.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, null, "%", "%")) {
                while (rs.next()) {
                    if (rs.getString("TABLE_NAME").equalsIgnoreCase(table)
                            && rs.getString("COLUMN_NAME").equalsIgnoreCase(column)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
