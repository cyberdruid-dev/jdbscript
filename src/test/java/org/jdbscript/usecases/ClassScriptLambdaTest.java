package org.jdbscript.usecases;

import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.db.ITestDBSchema;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

@Test
public class ClassScriptLambdaTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    public static abstract class LambdaScript implements ITestDBSchema {{
        List.of(1, 2).forEach(id -> table_1().id(id).str_column_1("lambda " + id));
    }}

    public static abstract class MethodReferenceScript implements ITestDBSchema {{
        List.of(1, 2).forEach(this::addRow);
    }

        private void addRow(int id) {
            table_1().id(id).str_column_1("method reference " + id);
        }
    }

    public static abstract class ExtendsLambdaScript extends LambdaScript {{
        List.of(3).forEach(id -> table_1().id(id).str_column_1("child " + id));
    }}

    public static abstract class IncludedScript implements ITestDBSchema {{
        table_1().id(1).str_column_1("included");
    }}

    public static abstract class IncludesInLambdaScript implements ITestDBSchema {{
        List.of(IncludedScript.class).forEach(this::include);
        List.of(2).forEach(id -> table_1().id(id).str_column_1("after include"));
    }}

    private final JDBEngine<ITestDBSchema> engine = createEngine(ITestDBSchema.class);

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables(TABLE_NAME_1);
    }

    public void class_script_should_run_a_lambda_calling_table_methods() {
        engine.resetDB(LambdaScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "lambda 1"),
                row(2, "lambda 2")
        ));
    }

    public void class_script_should_run_a_method_reference_calling_table_methods() {
        engine.resetDB(MethodReferenceScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "method reference 1"),
                row(2, "method reference 2")
        ));
    }

    public void class_script_should_run_lambdas_of_its_base_script() {
        engine.resetDB(ExtendsLambdaScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "lambda 1"),
                row(2, "lambda 2"),
                row(3, "child 3")
        ));
    }

    public void class_script_should_keep_working_after_an_include_inside_a_lambda() {
        engine.resetDB(IncludesInLambdaScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("id", "str_column_1"),
                row(1, "included"),
                row(2, "after include")
        ));
    }
}
