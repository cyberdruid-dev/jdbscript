package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.Consumer;

@Test
public class IncludeScriptTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    private interface ITable1Record extends IDBRecord {
        ITable1Record str_column_1(String value);
        ITable1Record str_column_2(String value);
    }
    private interface ITestSchema extends IDBSchema {

        ITable1Record table_1();

    }

    private final Consumer<ITestSchema> includedScript = (db)-> {
        db.table_1().str_column_1("one").str_column_2("two");
        db.table_1().str_column_1("three").str_column_2("four");
    };

    private static abstract class IncludedScriptClass implements ITestSchema {{
        table_1().str_column_1("class one").str_column_2("class two");
        table_1().str_column_1("class three").str_column_2("class four");
    }};

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME_1);
    }

    private final JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @Test
    public void included_script_should_be_added_to_result() {
        engine.resetDB((db)->{
            db.table_1().str_column_1("before one").str_column_2("before two");
            db.include(includedScript);
            db.table_1().str_column_1("after three").str_column_2("after four");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1", "str_column_2"),
                row("before one", "before two"),
                row("one", "two"),
                row("three", "four"),
                row("after three", "after four")
        ));
    }

    @Test
    public void included_class_script_should_be_added_to_result() {
        engine.resetDB((db)->{
            db.table_1().str_column_1("before one").str_column_2("before two");
            db.include(IncludedScriptClass.class);
            db.table_1().str_column_1("after three").str_column_2("after four");
        });

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1", "str_column_2"),
                row("before one", "before two"),
                row("class one", "class two"),
                row("class three", "class four"),
                row("after three", "after four")
        ));
    }
}
