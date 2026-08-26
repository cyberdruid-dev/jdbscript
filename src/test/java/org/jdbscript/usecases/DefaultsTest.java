package org.jdbscript.usecases;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class DefaultsTest extends JdbAbstractTest {

    private final static String TABLE_NAME = "table_with_defaults";

    public interface ITableWithDefaultsRecord extends IDBRecord {
        ITableWithDefaultsRecord int_column_1(Integer value);
        ITableWithDefaultsRecord str_column_1(String value);
        default void defaults() {
            int_column_1(10);
            str_column_1("default value");
        }
    }
    private interface IDefaultsTestSchema extends IDbSchema {

        ITableWithDefaultsRecord table_with_defaults();

    }
    private static abstract class ClassScriptWithDefaults implements IDefaultsTestSchema {{
        table_with_defaults().int_column_1(15);
    }};

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME);
    }

    private final JDBEngine<IDefaultsTestSchema> engine = createEngine(IDefaultsTestSchema.class);

    @Test
    public void if_no_column_values_specified_defaults_should_be_applied() {
        engine.resetDB((db)->{
            db.table_with_defaults();
        });

        assertTableValues(table(TABLE_NAME,
                columns("str_column_1", "int_column_1"),
                row("default value", 10)
        ));
    }

    @Test
    public void defaults_should_not_override_values_set_in_script() {
        engine.resetDB((db)->{
            db.table_with_defaults().int_column_1(15);
        });

        assertTableValues(table(TABLE_NAME,
                columns("str_column_1", "int_column_1"),
                row("default value", 15)
        ));
    }

    @Test(dependsOnMethods = "defaults_should_not_override_values_set_in_script")
    public void defaults_should_work_for_class_scripts() {
        engine.resetDB(ClassScriptWithDefaults.class);

        assertTableValues(table(TABLE_NAME,
                columns("str_column_1", "int_column_1"),
                row("default value", 15)
        ));
    }
}
