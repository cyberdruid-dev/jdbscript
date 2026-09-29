package org.jdbscript.usecases;

import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.db.ITestDBSchema;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.function.Consumer;

@Test
public class ClassScriptIncludeTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    private static final Consumer<ITestDBSchema> lambdaScript = db -> db.table_1().id(1).str_column_1("lambda");

    public static abstract class IncludesLambdaScript implements ITestDBSchema {{
        include(lambdaScript);
        table_1().id(2).str_column_1("class");
    }}

    public static abstract class BaseScript implements ITestDBSchema {{
        table_1().id(1).str_column_1("base");
    }}

    public static abstract class MiddleScript implements ITestDBSchema {{
        include(BaseScript.class);
        table_1().id(2).str_column_1("middle");
    }}

    public static abstract class TopScript implements ITestDBSchema {{
        include(MiddleScript.class);
        table_1().id(3).str_column_1("top");
    }}

    private final JDBEngine<ITestDBSchema> engine = createEngine(ITestDBSchema.class);

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
