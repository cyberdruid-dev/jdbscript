package org.jdbscript.usecases;

import org.jdbscript.IDbSchema;
import org.jdbscript.IDbSchema.IDBRecord;
import org.jdbscript.JDBEngine;
import org.jdbscript.JdbAbstractTest;
import org.jdbscript.RecordTools;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test
public class TemplateDefaultsTest extends JdbAbstractTest {

    private final static String TABLE_NAME = "table_with_defaults";

    public interface ITableWithDefaultsRecord extends IDBRecord {
        ITableWithDefaultsRecord int_column_1(Integer value);
        ITableWithDefaultsRecord str_column_1(String value);
        default void defaults(RecordTools tools) {
            int_column_1(10);
            str_column_1(tools.strValue("value=${int_column_1}"));
        }
    }
    private interface IDefaultsTestSchema extends IDbSchema {

        ITableWithDefaultsRecord table_with_defaults();

    }

    @BeforeMethod
    public void beforeMethod(){
        cleanupTables(TABLE_NAME);
    }

    private final JDBEngine<IDefaultsTestSchema> engine = createEngine(IDefaultsTestSchema.class);

    @Test
    public void strValue_should_substitute_template_variables_with_already_set_values() {
        engine.resetDB((db)->{
            db.table_with_defaults().int_column_1(777);
            db.table_with_defaults();
            db.table_with_defaults().str_column_1("value from script");
        });

        assertTableValues(table(TABLE_NAME,
                columns("str_column_1", "int_column_1"),
                row("value=777", 777),
                row("value=10", 10),
                row("value from script", 10)
        ));
    }

}
