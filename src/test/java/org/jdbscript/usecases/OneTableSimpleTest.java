package org.jdbscript.usecases;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.IJDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Test
public class OneTableSimpleTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    private interface ITable1Record extends IDBRecord {
        ITable1Record str_column_1(String value);
        ITable1Record str_column_2(String value);
    }
    private interface ITestSchema extends IDbSchema {

        ITable1Record table_1();

    }

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME_1);
    }

    private final IJDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @Test
    public void testResetOneColumn() {
        engine.resetDB((db)->{
            db.table_1().str_column_1("Hello");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("Hello")
        ));
    }

    @Test
    public void testInsertOneColumn() {
        engine.insertDB((db)->{
            db.table_1().str_column_1("Hello");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("Hello")
        ));
    }

    @Test
    public void testInsertMultipleColumns() {
        engine.insertDB((db)->{
            db.table_1().str_column_1("Hello").str_column_2("Goodbye");
            db.table_1().str_column_1("Next Hello").str_column_2("New Goodbye");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1",   "str_column_2"),
                row("Hello",      "Goodbye"),
                row("Next Hello", "New Goodbye")
        ));
    }

    @Test
    public void insert_should_not_remove_existing_records() {
        executeUpdate("INSERT INTO %s (str_column_1) VALUES ('Before Hello')", TABLE_NAME_1);
        engine.insertDB((db)->{
            db.table_1().str_column_1("Hello");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("Before Hello"),
                row("Hello")
        ));
    }

    @Test
    public void reset_should_remove_existing_records() {
        executeUpdate("INSERT INTO %s (str_column_1) VALUES ('Before Hello')", TABLE_NAME_1);
        engine.resetDB((db)->{
            db.table_1().str_column_1("Hello");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("Hello")
        ));
    }


    @Test
    public void cleanupDB_should_remove_all_records() {
        executeUpdate("INSERT INTO %s (str_column_1) VALUES ('Before Hello')", TABLE_NAME_1);
        engine.cleanupDB();

        assertTableEmpty(TABLE_NAME_1);
    }
}
