package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class SequenceCleanupResetTest extends JdbAbstractTest {

    private final static String TABLE_NAME_GENERATED = "generated_int_id_table";

    private interface IGeneratedIntIdTableRecord extends IDBRecord {
        IGeneratedIntIdTableRecord generated_id_column(Integer value);
        IGeneratedIntIdTableRecord varchar_column(String value);
    }
    private interface ITestSchema extends IDBSchema {
        IGeneratedIntIdTableRecord generated_int_id_table();
    }

    @BeforeMethod
    public void beforeMethod() {
        // The rest don't manage sequences at all, or (DuckDB) can only ever advance one, never
        // rewind it to an exact value - see DuckdbSequenceResetTest for DuckDB's own guarantee.
        skipUnless("sequence reset on cleanup", DBMSType.ORACLE, DBMSType.HSQLDB, DBMSType.DB2,
                DBMSType.POSTGRESQL, DBMSType.COCKROACHDB);
        cleanupTables(TABLE_NAME_GENERATED);
    }

    private final JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @Test
    public void first_reset_should_assign_the_predictable_floor_value() {
        engine.resetDB(db -> db.generated_int_id_table().varchar_column("a"));

        assertTableValues(table(TABLE_NAME_GENERATED,
                columns("generated_id_column", "varchar_column"),
                row(10000, "a")
        ));
    }

    @Test
    public void second_reset_cycle_should_restart_from_the_floor_not_keep_growing() {
        engine.resetDB(db -> {
            db.generated_int_id_table().varchar_column("a");
            db.generated_int_id_table().varchar_column("b");
        });

        engine.resetDB(db -> db.generated_int_id_table().varchar_column("c"));

        assertTableValues(table(TABLE_NAME_GENERATED,
                columns("generated_id_column", "varchar_column"),
                row(10000, "c")
        ));
    }
}
