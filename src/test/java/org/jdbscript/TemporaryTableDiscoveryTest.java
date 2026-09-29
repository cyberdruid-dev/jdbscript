package org.jdbscript;

import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.db.ITable1Record;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * A global temporary table (e.g. Hibernate 6's HTE_* bulk-id tables) holds only session-private
 * rows, so it's never a fixture target and must not fail strict unmapped-table validation of a
 * schema interface that maps every standing table.
 */
@Test
public class TemporaryTableDiscoveryTest extends JdbAbstractTest {

    private static final String TEMP_TABLE = "tmp_discovery_check";
    // Only these accept this exact CREATE GLOBAL TEMPORARY TABLE syntax.
    private static final DBMSType[] WITH_GLOBAL_TEMPORARY_TABLES = {DBMSType.ORACLE, DBMSType.H2, DBMSType.HSQLDB};

    private interface ITable extends IDBRecord {
    }

    private interface IStandingSchema extends IDBSchema {
        ITable1Record table_1();
        ITable table_2();
        ITable table_with_defaults();
        ITable int_table();
        ITable varchar_table();
        ITable boolean_table();
        ITable date_table();
        ITable timestamp_table();
        ITable uuid_table();
        ITable blob_table();
        ITable generated_int_id_table();
        ITable table_for_assertions();
        ITable customers();
        ITable orders();
        ITable order_items();
        ITable composite_pk_table();
        ITable no_pk_table();
    }

    @BeforeMethod
    public void beforeMethod() {
        skipUnless("global temporary tables", WITH_GLOBAL_TEMPORARY_TABLES);
        cleanupTables("table_1");
        executeUpdate("CREATE GLOBAL TEMPORARY TABLE " + TEMP_TABLE + " (id INT)");
    }

    @AfterMethod(alwaysRun = true)
    public void afterMethod() {
        forAny(() -> executeUpdate("DROP TABLE " + TEMP_TABLE), WITH_GLOBAL_TEMPORARY_TABLES);
    }

    // After the migration tests: until their group ends, their migration_* tables are in the schema.
    @Test(dependsOnGroups = "migration", alwaysRun = true, ignoreMissingDependencies = true)
    public void strict_validation_should_ignore_global_temporary_tables() {
        JDBEngine<IStandingSchema> engine = engineBuilder(IStandingSchema.class)
                .cacheStrategy(CacheStrategy.NONE)
                .unmappedTableStrategy(ValidationStrategy.FAIL)
                .build();

        engine.insertDB(db -> db.table_1().id(1).str_column_1("inserted"));

        assertTableValues(table("table_1",
                columns("id", "str_column_1"),
                row(1, "inserted")
        ));
    }
}
