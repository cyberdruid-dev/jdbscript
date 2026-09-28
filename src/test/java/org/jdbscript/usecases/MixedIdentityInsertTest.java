package org.jdbscript.usecases;

import org.jdbscript.DBMSType;
import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * A single insert script can freely mix records that supply an identity/auto-increment column's
 * value with records that leave it to the database - expected on every DBMS, though MSSQL is the
 * one that needs explicit handling for it (IDENTITY_INSERT toggling).
 */
@Test
public class MixedIdentityInsertTest extends JdbAbstractTest {
    private final static String TABLE_NAME_1 = "table_1";
    private final static String TABLE_NAME_GENERATED = "generated_int_id_table";

    private interface ITable1RecordWithId extends IDBRecord {
        ITable1RecordWithId id(Long value);
        ITable1RecordWithId str_column_1(String value);
    }
    private interface IGeneratedIntIdTableRecord extends IDBRecord {
        IGeneratedIntIdTableRecord generated_id_column(Integer value);
        IGeneratedIntIdTableRecord varchar_column(String value);
    }
    private interface ITestSchema extends IDBSchema {
        ITable1RecordWithId table_1();
        IGeneratedIntIdTableRecord generated_int_id_table();
    }

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME_1, TABLE_NAME_GENERATED);
    }

    private final JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @Test
    public void mixed_explicit_and_implicit_ids_in_same_script_should_both_work(){
        skipFor("Native auto-increment", DBMSType.SPANNER);
        engine.insertDB((db)->{
            db.table_1().str_column_1("no-id-1");
            db.table_1().id(999999L).str_column_1("with-id");
            db.table_1().str_column_1("no-id-2");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("no-id-1"),
                row("with-id"),
                row("no-id-2")
        ));
    }

    @Test
    public void interleaved_tables_each_needing_explicit_ids_should_both_work(){
        engine.insertDB((db)->{
            db.table_1().id(888888L).str_column_1("t1-a");
            db.generated_int_id_table().generated_id_column(777777).varchar_column("g-a");
            db.table_1().id(888889L).str_column_1("t1-b");
            db.generated_int_id_table().generated_id_column(777778).varchar_column("g-b");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("t1-a"),
                row("t1-b")
        ));
        assertTableValues(table(TABLE_NAME_GENERATED,
                columns("varchar_column"),
                row("g-a"),
                row("g-b")
        ));
    }
}
