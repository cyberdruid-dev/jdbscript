package org.jdbscript.usecases;

import org.jdbscript.IDBSchema;
import org.jdbscript.IDBSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.Consumer;

@Test
public class ClassScriptIncludeTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    private interface ITable1Record extends IDBRecord {
        ITable1Record id(int value);
        ITable1Record str_column_1(String value);
    }
    private interface ITestSchema extends IDBSchema {
        ITable1Record table_1();
    }

    private static final Consumer<ITestSchema> lambdaScript = db -> db.table_1().id(1).str_column_1("lambda");

    public static abstract class IncludesLambdaScript implements ITestSchema {{
        include(lambdaScript);
        table_1().id(2).str_column_1("class");
    }}

    public static abstract class BaseScript implements ITestSchema {{
        table_1().id(1).str_column_1("base");
    }}

    public static abstract class MiddleScript implements ITestSchema {{
        include(BaseScript.class);
        table_1().id(2).str_column_1("middle");
    }}

    public static abstract class TopScript implements ITestSchema {{
        include(MiddleScript.class);
        table_1().id(3).str_column_1("top");
    }}

    private final JDBEngine<ITestSchema> engine = createEngine(ITestSchema.class);

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables(TABLE_NAME_1);
    }

    public void class_script_should_include_lambda_script() {
        engine.resetDB(IncludesLambdaScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("lambda"),
                row("class")
        ));
    }

    public void class_script_should_include_class_script() {
        engine.resetDB(MiddleScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("base"),
                row("middle")
        ));
    }

    public void class_script_includes_should_chain() {
        engine.resetDB(TopScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("base"),
                row("middle"),
                row("top")
        ));
    }
}
