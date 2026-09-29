package org.jdbscript.usecases;

import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.db.ITestDBSchema;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class ClassScriptOuterAccessTest extends JdbAbstractTest {

    private final static String TABLE_NAME_1 = "table_1";

    // Not a compile-time constant: a literal would be inlined and never touch the field.
    private static final String PRIVATE_FIELD_VALUE = String.valueOf("private field");

    private static String privateMethodValue() {
        return "private method";
    }

    public static abstract class ReadsOuterPrivateField implements ITestDBSchema {{
        table_1().id(1).str_column_1(PRIVATE_FIELD_VALUE);
    }}

    public static abstract class CallsOuterPrivateMethod implements ITestDBSchema {{
        table_1().id(1).str_column_1(privateMethodValue());
    }}

    private static abstract class PrivateBaseScript implements ITestDBSchema {{
        table_1().id(1).str_column_1("private base");
    }}

    public static abstract class ExtendsPrivateBaseScript extends PrivateBaseScript {{
        table_1().id(2).str_column_1("child");
    }}

    private final JDBEngine<ITestDBSchema> engine = createEngine(ITestDBSchema.class);

    @BeforeMethod
    public void beforeMethod() {
        cleanupTables(TABLE_NAME_1);
    }

    public void class_script_should_read_private_static_field_of_outer_class() {
        engine.resetDB(ReadsOuterPrivateField.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("private field")
        ));
    }

    public void class_script_should_call_private_static_method_of_outer_class() {
        engine.resetDB(CallsOuterPrivateMethod.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("private method")
        ));
    }

    public void class_script_should_extend_private_sibling_script_class() {
        engine.resetDB(ExtendsPrivateBaseScript.class);

        assertTableValues(table(TABLE_NAME_1,
                columns("str_column_1"),
                row("private base"),
                row("child")
        ));
    }
}
